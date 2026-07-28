package hs.kr.entrydsm.gateway.adapterin.resilience

import hs.kr.entrydsm.gateway.adapterin.configuration.GatewayRuntimeProperties
import reactor.core.publisher.Mono

data class GatewayCircuitPermit(
    val allowed: Boolean,
    val halfOpen: Boolean,
)

interface GatewayCircuitStateStore {
    fun tryAcquire(
        routeId: String,
        policy: GatewayRuntimeProperties.Resilience,
    ): Mono<GatewayCircuitPermit>

    fun releaseHalfOpen(routeId: String): Mono<Void>

    fun record(
        routeId: String,
        failed: Boolean,
        halfOpen: Boolean,
        policy: GatewayRuntimeProperties.Resilience,
    ): Mono<Void>
}
