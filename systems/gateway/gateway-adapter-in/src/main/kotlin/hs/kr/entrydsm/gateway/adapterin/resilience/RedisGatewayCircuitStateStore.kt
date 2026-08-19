package hs.kr.entrydsm.gateway.adapterin.resilience

import hs.kr.entrydsm.gateway.adapterin.configuration.GatewayRuntimeProperties
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono
import java.time.Duration
import java.util.UUID

@Component
@ConditionalOnProperty(
    prefix = "gateway.resilience",
    name = ["state-store"],
    havingValue = "redis",
    matchIfMissing = true,
)
class RedisGatewayCircuitStateStore(
    private val redis: ReactiveStringRedisTemplate,
) : GatewayCircuitStateStore {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun tryAcquire(
        routeId: String,
        policy: GatewayRuntimeProperties.Resilience,
    ): Mono<GatewayCircuitPermit> {
        val permitId = UUID.randomUUID().toString()
        val result = redis.execute(
            ACQUIRE_SCRIPT,
            listOf(key(routeId, "open"), key(routeId, "probe"), permitKey(routeId, permitId)),
            listOf(
                System.currentTimeMillis().toString(),
                policy.permittedNumberOfCallsInHalfOpenState.toString(),
                GatewayCircuitStateStore.PROBE_TIMEOUT_SECONDS.toString(),
            ),
        ).next()

        return result
            .defaultIfEmpty(CLOSED)
            .map { state ->
                when (state) {
                    HALF_OPEN -> GatewayCircuitPermit(true, true, permitId)
                    DENIED -> GatewayCircuitPermit(false, false)
                    else -> GatewayCircuitPermit(true, false)
                }
            }
            .doOnError { error -> logger.warn("Redis circuit acquire failed for route {}", routeId, error) }
            .onErrorReturn(GatewayCircuitPermit(true, false))
    }

    override fun releaseHalfOpen(routeId: String, permitId: String?): Mono<Void> {
        if (permitId == null) return Mono.empty()
        return redis.execute(
            RELEASE_SCRIPT,
            listOf(permitKey(routeId, permitId), key(routeId, "probe")),
            emptyList<Any>(),
        ).then()
            .doOnError { error -> logger.warn("Redis half-open release failed for route {}", routeId, error) }
            .onErrorResume { Mono.empty() }
    }

    override fun record(
        routeId: String,
        failed: Boolean,
        halfOpen: Boolean,
        policy: GatewayRuntimeProperties.Resilience,
        permitId: String?,
    ): Mono<Void> {
        if (halfOpen) {
            if (permitId == null) return Mono.empty()
            return redis.execute(
                HALF_OPEN_RECORD_SCRIPT,
                listOf(
                    permitKey(routeId, permitId),
                    key(routeId, "open"),
                    key(routeId, "probe"),
                    key(routeId, "events"),
                ),
                listOf(
                    if (failed) "1" else "0",
                    openUntil(policy),
                    (policy.waitDurationSeconds + GatewayCircuitStateStore.PROBE_TIMEOUT_SECONDS).toString(),
                ),
            ).then()
                .doOnError { error -> logger.warn("Redis half-open record failed for route {}", routeId, error) }
                .onErrorResume { Mono.empty() }
        }

        return redis.execute(
            RECORD_SCRIPT,
            listOf(key(routeId, "events"), key(routeId, "open")),
            listOf(
                if (failed) "1" else "0",
                policy.slidingWindowSize.toString(),
                policy.minimumNumberOfCalls.toString(),
                policy.failureRateThreshold.toString(),
                openUntil(policy),
                (policy.waitDurationSeconds + GatewayCircuitStateStore.PROBE_TIMEOUT_SECONDS).toString(),
                (policy.waitDurationSeconds * 2).toString(),
            ),
        ).then()
            .doOnError { error -> logger.warn("Redis circuit record failed for route {}", routeId, error) }
            .onErrorResume { Mono.empty() }
    }

    private fun permitKey(routeId: String, permitId: String): String = key(routeId, "permit:$permitId")

    private fun key(routeId: String, suffix: String): String = "$KEY_PREFIX:$routeId:$suffix"

    private fun openUntil(policy: GatewayRuntimeProperties.Resilience): String =
        (System.currentTimeMillis() + policy.waitDurationSeconds * 1_000).toString()

    private companion object {
        const val KEY_PREFIX = "gateway:circuit"
        const val CLOSED = "CLOSED"
        const val DENIED = "DENIED"
        const val HALF_OPEN = "HALF_OPEN"

        val ACQUIRE_SCRIPT = DefaultRedisScript(
            """
            local openUntil = redis.call('GET', KEYS[1])
            if not openUntil then return 'CLOSED' end
            if tonumber(openUntil) > tonumber(ARGV[1]) then return 'DENIED' end
            local permits = redis.call('INCR', KEYS[2])
            if permits > tonumber(ARGV[2]) then
                redis.call('DECR', KEYS[2])
                return 'DENIED'
            end
            redis.call('SET', KEYS[3], '1', 'EX', ARGV[3])
            redis.call('EXPIRE', KEYS[2], ARGV[3])
            return 'HALF_OPEN'
            """.trimIndent(),
            String::class.java,
        )

        val RELEASE_SCRIPT = DefaultRedisScript(
            """
            if redis.call('EXISTS', KEYS[1]) == 1 then
                redis.call('DEL', KEYS[1])
                local permits = redis.call('GET', KEYS[2])
                if permits and tonumber(permits) > 0 then redis.call('DECR', KEYS[2]) end
            end
            return 'OK'
            """.trimIndent(),
            String::class.java,
        )

        val HALF_OPEN_RECORD_SCRIPT = DefaultRedisScript(
            """
            if redis.call('EXISTS', KEYS[1]) == 0 then return 'IGNORED' end
            redis.call('DEL', KEYS[1])
            if ARGV[1] == '1' then
                redis.call('SET', KEYS[2], ARGV[2], 'PX', tonumber(ARGV[3]) * 1000)
            else
                redis.call('DEL', KEYS[2], KEYS[3], KEYS[4])
            end
            return 'OK'
            """.trimIndent(),
            String::class.java,
        )

        val RECORD_SCRIPT = DefaultRedisScript(
            """
            redis.call('RPUSH', KEYS[1], ARGV[1])
            redis.call('LTRIM', KEYS[1], -tonumber(ARGV[2]), -1)
            local events = redis.call('LRANGE', KEYS[1], 0, -1)
            local failures = 0
            for _, event in ipairs(events) do
                if event == '1' then failures = failures + 1 end
            end
            if #events >= tonumber(ARGV[3]) and failures * 100.0 / #events >= tonumber(ARGV[4]) then
                redis.call('SET', KEYS[2], ARGV[5], 'PX', tonumber(ARGV[6]) * 1000)
            end
            redis.call('EXPIRE', KEYS[1], tonumber(ARGV[7]))
            return 'OK'
            """.trimIndent(),
            String::class.java,
        )
    }
}
