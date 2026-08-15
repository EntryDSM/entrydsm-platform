package hs.kr.entrydsm.gateway.adapterin.resilience

import hs.kr.entrydsm.gateway.adapterin.configuration.GatewayRuntimeProperties
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@Component
@ConditionalOnProperty(prefix = "gateway.resilience", name = ["state-store"], havingValue = "memory")
class InMemoryGatewayCircuitStateStore(
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val probeTimeoutMillis: Long = GatewayCircuitStateStore.PROBE_TIMEOUT_SECONDS * 1_000,
) : GatewayCircuitStateStore {
    private val states = ConcurrentHashMap<String, State>()

    override fun tryAcquire(
        routeId: String,
        policy: GatewayRuntimeProperties.Resilience,
    ): Mono<GatewayCircuitPermit> = Mono.fromSupplier {
        synchronized(states.computeIfAbsent(routeId) { State() }) {
            val state = states[routeId]!!
            val now = nowMillis()
            when {
                state.openedUntilMillis == 0L -> GatewayCircuitPermit(true, false)
                state.openedUntilMillis > now -> GatewayCircuitPermit(false, false)
                else -> {
                    state.halfOpenPermits.entries.removeIf { (_, expiresAtMillis) -> expiresAtMillis <= now }
                    if (state.halfOpenPermits.size >= policy.permittedNumberOfCallsInHalfOpenState) {
                        GatewayCircuitPermit(false, false)
                    } else {
                        val permitId = UUID.randomUUID().toString()
                        state.halfOpenPermits[permitId] = now + probeTimeoutMillis
                        GatewayCircuitPermit(true, true, permitId)
                    }
                }
            }
        }
    }

    override fun releaseHalfOpen(routeId: String, permitId: String?): Mono<Void> = Mono.fromRunnable {
        states[routeId]?.let { state ->
            synchronized(state) {
                permitId?.let { state.halfOpenPermits.remove(it) }
            }
        }
    }

    override fun record(
        routeId: String,
        failed: Boolean,
        halfOpen: Boolean,
        policy: GatewayRuntimeProperties.Resilience,
        permitId: String? = null,
    ): Mono<Void> = Mono.fromRunnable {
        val state = states.computeIfAbsent(routeId) { State() }
        synchronized(state) {
            if (halfOpen) {
                if (permitId == null || state.halfOpenPermits.remove(permitId) == null) {
                    return@fromRunnable
                }
                if (failed) {
                    state.openedUntilMillis = nowMillis() + policy.waitDurationSeconds * 1_000
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
                state.openedUntilMillis = nowMillis() + policy.waitDurationSeconds * 1_000
            }
        }
    }

    private class State {
        val events = ArrayDeque<Boolean>()
        var openedUntilMillis: Long = 0L
        val halfOpenPermits = mutableMapOf<String, Long>()
    }
}
