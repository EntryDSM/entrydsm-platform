package hs.kr.entrydsm.gateway.adapterin.resilience

import org.springframework.boot.actuate.endpoint.annotation.ReadOperation
import org.springframework.boot.actuate.endpoint.annotation.Endpoint
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

@Component
@Endpoint(id = "gatewayCircuitBreakers")
class GatewayCircuitBreakerMetrics {
    private val counters = ConcurrentHashMap<String, Counters>()

    fun recordFailure(routeId: String) = counters(routeId).failures.incrementAndGet()

    fun recordSuccess(routeId: String) = counters(routeId).successes.incrementAndGet()

    fun recordOpened(routeId: String) = counters(routeId).opened.incrementAndGet()

    fun recordRejected(routeId: String) = counters(routeId).rejected.incrementAndGet()

    @ReadOperation
    fun snapshot(): Map<String, Map<String, Long>> = counters.mapValues { (_, value) ->
        mapOf(
            "failures" to value.failures.get(),
            "successes" to value.successes.get(),
            "opened" to value.opened.get(),
            "rejected" to value.rejected.get(),
        )
    }

    private fun counters(routeId: String): Counters = counters.computeIfAbsent(routeId) { Counters() }

    private class Counters {
        val failures = AtomicLong()
        val successes = AtomicLong()
        val opened = AtomicLong()
        val rejected = AtomicLong()
    }
}
