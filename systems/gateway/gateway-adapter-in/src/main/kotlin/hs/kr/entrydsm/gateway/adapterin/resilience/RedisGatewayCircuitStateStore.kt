package hs.kr.entrydsm.gateway.adapterin.resilience

import hs.kr.entrydsm.gateway.adapterin.configuration.GatewayRuntimeProperties
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.stereotype.Component
import org.slf4j.LoggerFactory
import reactor.core.publisher.Mono
import java.time.Duration

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
        val openKey = key(routeId, "open")
        val probeKey = key(routeId, "probe")
        return redis.opsForValue().get(openKey)
            .flatMap { openedUntil ->
                if (openedUntil.toLongOrNull()?.let { it > System.currentTimeMillis() } != false) {
                    Mono.just(GatewayCircuitPermit(false, false))
                } else {
                    acquireHalfOpen(probeKey, policy)
                }
            }
            .defaultIfEmpty(GatewayCircuitPermit(true, false))
            .doOnError { error -> logger.warn("Redis circuit acquire failed for route {}", routeId, error) }
            .onErrorReturn(GatewayCircuitPermit(true, false))
    }

    override fun releaseHalfOpen(routeId: String, permitId: String?): Mono<Void> =
        redis.opsForValue().decrement(key(routeId, "probe")).then()

    override fun record(
        routeId: String,
        failed: Boolean,
        halfOpen: Boolean,
        policy: GatewayRuntimeProperties.Resilience,
        permitId: String?,
    ): Mono<Void> {
        if (halfOpen) {
            return if (failed) {
                redis.opsForValue()
                    .set(
                        key(routeId, "open"),
                        openUntil(policy),
                        Duration.ofSeconds(policy.waitDurationSeconds + GatewayCircuitStateStore.PROBE_TIMEOUT_SECONDS),
                    )
                    .then(redis.delete(key(routeId, "probe")))
                    .then()
            } else {
                redis.delete(key(routeId, "open"), key(routeId, "probe"), key(routeId, "events")).then()
            }
        }

        val eventsKey = key(routeId, "events")
        return redis.opsForList()
            .rightPush(eventsKey, if (failed) "1" else "0")
            .then(redis.opsForList().trim(eventsKey, -policy.slidingWindowSize.toLong(), -1))
            .then(redis.opsForList().range(eventsKey, 0, -1).collectList())
            .flatMap { events ->
                val failures = events.count { it == "1" }
                val opens = events.size >= policy.minimumNumberOfCalls &&
                    failures * 100.0 / events.size >= policy.failureRateThreshold
                if (opens) {
                    redis.opsForValue()
                        .set(
                            key(routeId, "open"),
                            openUntil(policy),
                            Duration.ofSeconds(policy.waitDurationSeconds + GatewayCircuitStateStore.PROBE_TIMEOUT_SECONDS),
                        )
                } else {
                    Mono.empty()
                }
            }
            .then(redis.expire(eventsKey, Duration.ofSeconds(policy.waitDurationSeconds * 2)))
            .doOnError { error -> logger.warn("Redis circuit record failed for route {}", routeId, error) }
            .onErrorResume { Mono.empty() }
            .then()
    }

    private fun key(routeId: String, suffix: String): String = "$KEY_PREFIX:$routeId:$suffix"

    private fun acquireHalfOpen(
        probeKey: String,
        policy: GatewayRuntimeProperties.Resilience,
    ): Mono<GatewayCircuitPermit> = redis.opsForValue()
        .increment(probeKey)
        .flatMap { permits ->
            if (permits <= policy.permittedNumberOfCallsInHalfOpenState) {
                redis.expire(
                    probeKey,
                    Duration.ofSeconds(GatewayCircuitStateStore.PROBE_TIMEOUT_SECONDS),
                )
                    .thenReturn(GatewayCircuitPermit(true, true))
            } else {
                redis.opsForValue().decrement(probeKey)
                    .thenReturn(GatewayCircuitPermit(false, false))
            }
        }

    private fun openUntil(policy: GatewayRuntimeProperties.Resilience): String =
        (System.currentTimeMillis() + policy.waitDurationSeconds * 1_000).toString()

    private companion object {
        const val KEY_PREFIX = "gateway:circuit"
    }
}
