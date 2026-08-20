package hs.kr.entrydsm.gateway.adapterin.resilience

import hs.kr.entrydsm.gateway.adapterin.configuration.GatewayRuntimeProperties
import hs.kr.entrydsm.gateway.adapterin.error.GatewayErrorResponseWriter

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import org.springframework.cloud.gateway.filter.GatewayFilterChain
import org.springframework.cloud.gateway.filter.GlobalFilter
import org.springframework.cloud.gateway.route.Route
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils
import org.springframework.core.Ordered
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit

@Component
class GatewayCircuitBreakerGlobalFilter(
    properties: GatewayRuntimeProperties,
    private val circuitBreakerRegistry: CircuitBreakerRegistry,
    private val stateStore: GatewayCircuitStateStore,
    private val responseWriter: GatewayErrorResponseWriter,
) : GlobalFilter, Ordered {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val policy = properties.resilience
    private val openDurationSeconds = policy.waitDurationSeconds

    override fun filter(exchange: ServerWebExchange, chain: GatewayFilterChain): Mono<Void> {
        val routeId = exchange.getAttribute<Route>(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR)?.id
            ?: return chain.filter(exchange)
        val circuitBreaker = circuitBreakerRegistry.circuitBreaker(routeId)
        val startedAt = System.nanoTime()

        return stateStore.tryAcquire(routeId, policy)
            .doOnError { error -> logger.warn("Circuit state acquire failed for route {}", routeId, error) }
            .onErrorReturn(GatewayCircuitPermit(allowed = true, halfOpen = false))
            .flatMap { sharedPermit ->
                if (!sharedPermit.allowed) {
                    return@flatMap reject(exchange)
                }
                chain.filter(exchange)
                    .onErrorResume { error ->
                        circuitBreaker.onError(
                            System.nanoTime() - startedAt,
                            TimeUnit.NANOSECONDS,
                            error,
                        )
                        recordSafely(
                            routeId,
                            failed = true,
                            permit = sharedPermit,
                            policy = policy,
                        ).then(Mono.error(error))
                    }
                    .then(Mono.defer {
                        val failed = exchange.response.statusCode?.is5xxServerError == true
                        val duration = System.nanoTime() - startedAt
                        if (failed) {
                            circuitBreaker.onError(
                                duration,
                                TimeUnit.NANOSECONDS,
                                DownstreamResponseFailure(routeId),
                            )
                        } else {
                            circuitBreaker.onSuccess(duration, TimeUnit.NANOSECONDS)
                        }
                        recordSafely(
                            routeId,
                            failed,
                            permit = sharedPermit,
                            policy = policy,
                        )
                    })
            }
    }

    override fun getOrder(): Int = Ordered.LOWEST_PRECEDENCE - 100

    private fun reject(exchange: ServerWebExchange): Mono<Void> {
        exchange.response.headers.add("Retry-After", openDurationSeconds.toString())
        return responseWriter.write(exchange, HttpStatus.SERVICE_UNAVAILABLE, "CIRCUIT_OPEN")
    }

    private fun recordSafely(
        routeId: String,
        failed: Boolean,
        permit: GatewayCircuitPermit,
        policy: GatewayRuntimeProperties.Resilience,
    ): Mono<Void> = stateStore.record(
        routeId,
        failed,
        permit.halfOpen,
        policy,
        permit.permitId,
    ).doOnError { error -> logger.warn("Circuit state record failed for route {}", routeId, error) }
        .onErrorResume { Mono.empty() }

    private class DownstreamResponseFailure(routeId: String) :
        RuntimeException(
            "Downstream service returned a 5xx response for route $routeId",
            null,
            false,
            false,
        )
}
