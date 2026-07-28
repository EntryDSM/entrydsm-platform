package hs.kr.entrydsm.gateway.adapterin.resilience

import hs.kr.entrydsm.gateway.adapterin.configuration.GatewayRuntimeProperties
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono
import java.util.concurrent.ConcurrentHashMap

@Component
@ConditionalOnProperty(prefix = "gateway.resilience", name = ["state-store"], havingValue = "memory")
class InMemoryGatewayCircuitStateStore : GatewayCircuitStateStore {
    private val states = ConcurrentHashMap<String, State>()

    override fun tryAcquire(
        routeId: String,
        policy: GatewayRuntimeProperties.Resilience,
    ): Mono<GatewayCircuitPermit> = Mono.fromSupplier {
        synchronized(states.computeIfAbsent(routeId) { State() }) {
            val state = states[routeId]!!
            val now = System.currentTimeMillis()
            when {
                state.openedUntilMillis == 0L -> GatewayCircuitPermit(true, false)
                state.openedUntilMillis > now -> GatewayCircuitPermit(false, false)
                state.halfOpenPermits >= policy.permittedNumberOfCallsInHalfOpenState ->
                    GatewayCircuitPermit(false, false)
                else -> {
                    state.halfOpenPermits++
                    GatewayCircuitPermit(true, true)
                }
            }
        }
    }

    override fun releaseHalfOpen(routeId: String): Mono<Void> = Mono.fromRunnable {
        states[routeId]?.let { state ->
            synchronized(state) {
                state.halfOpenPermits = (state.halfOpenPermits - 1).coerceAtLeast(0)
            }
        }
    }

    override fun record(
        routeId: String,
        failed: Boolean,
        halfOpen: Boolean,
        policy: GatewayRuntimeProperties.Resilience,
    ): Mono<Void> = Mono.fromRunnable {
        val state = states.computeIfAbsent(routeId) { State() }
        synchronized(state) {
            if (halfOpen) {
                state.halfOpenPermits = (state.halfOpenPermits - 1).coerceAtLeast(0)
                if (failed) {
                    state.openedUntilMillis = System.currentTimeMillis() + policy.waitDurationSeconds * 1_000
                } else {
                    state.openedUntilMillis = 0L
                    state.events.clear()
                }
                return@fromRunnable
            }
            state.events.addLast(failed)
            while (state.events.size > policy.slidingWindowSize) {
                state.events.removeFirst()
            }
            val calls = state.events.size
            val failures = state.events.count { it }
            if (calls >= policy.minimumNumberOfCalls && failures * 100.0 / calls >= policy.failureRateThreshold) {
                state.openedUntilMillis = System.currentTimeMillis() + policy.waitDurationSeconds * 1_000
            }
        }
    }

    private class State {
        val events = ArrayDeque<Boolean>()
        var openedUntilMillis: Long = 0L
        var halfOpenPermits: Int = 0
    }
}
