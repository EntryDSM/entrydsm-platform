package hs.kr.entrydsm.gateway.adapterin.resilience

import hs.kr.entrydsm.gateway.adapterin.configuration.GatewayRuntimeProperties
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.stereotype.Component
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
    override fun tryAcquire(
        routeId: String,
        policy: GatewayRuntimeProperties.Resilience,
    ): Mono<GatewayCircuitPermit> {
        val openKey = key(routeId, "open")
        val probeKey = key(routeId, "probe")
        return redis.hasKey(openKey)
            .flatMap { opened ->
                if (!opened) {
                    Mono.just(GatewayCircuitPermit(true, false))
                } else {
                    redis.opsForValue()
                        .increment(probeKey)
                        .flatMap { permits ->
                            if (permits <= policy.permittedNumberOfCallsInHalfOpenState) {
                                redis.expire(probeKey, Duration.ofSeconds(PROBE_TTL_SECONDS))
                                    .thenReturn(GatewayCircuitPermit(true, true))
                            } else {
                                redis.opsForValue().decrement(probeKey)
                                    .thenReturn(GatewayCircuitPermit(false, false))
                            }
                        }
                }
            }
            .onErrorReturn(GatewayCircuitPermit(true, false))
    }

    override fun releaseHalfOpen(routeId: String): Mono<Void> =
        redis.opsForValue().decrement(key(routeId, "probe")).then()

    override fun record(
        routeId: String,
        failed: Boolean,
        halfOpen: Boolean,
        policy: GatewayRuntimeProperties.Resilience,
    ): Mono<Void> {
        if (halfOpen) {
            return if (failed) {
                redis.opsForValue()
                    .set(key(routeId, "open"), "1", Duration.ofSeconds(policy.waitDurationSeconds))
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
                        .set(key(routeId, "open"), "1", Duration.ofSeconds(policy.waitDurationSeconds))
                } else {
                    Mono.empty()
                }
            }
            .then(redis.expire(eventsKey, Duration.ofSeconds(policy.waitDurationSeconds * 2)))
            .onErrorResume { Mono.empty() }
            .then()
    }

    private fun key(routeId: String, suffix: String): String = "$KEY_PREFIX:$routeId:$suffix"

    private companion object {
        const val KEY_PREFIX = "gateway:circuit"
        const val PROBE_TTL_SECONDS = 30L
    }
}
