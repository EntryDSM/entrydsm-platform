package hs.kr.entrydsm.gateway.adapterin.resilience

import hs.kr.entrydsm.gateway.adapterin.configuration.GatewayRuntimeProperties
import reactor.core.publisher.Mono

data class GatewayCircuitPermit(
    val allowed: Boolean,
    val halfOpen: Boolean,
    val permitId: String? = null,
)

interface GatewayCircuitStateStore {
    fun tryAcquire(
        routeId: String,
        policy: GatewayRuntimeProperties.Resilience,
    ): Mono<GatewayCircuitPermit>

    fun releaseHalfOpen(routeId: String, permitId: String? = null): Mono<Void>

    fun record(
        routeId: String,
        failed: Boolean,
        halfOpen: Boolean,
        policy: GatewayRuntimeProperties.Resilience,
        permitId: String? = null,
    ): Mono<Void>

    companion object {
        const val PROBE_TIMEOUT_SECONDS = 30L
    }
}
