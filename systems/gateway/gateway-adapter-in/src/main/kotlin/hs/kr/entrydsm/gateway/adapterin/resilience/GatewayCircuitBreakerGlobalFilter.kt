package hs.kr.entrydsm.gateway.adapterin.resilience

import hs.kr.entrydsm.gateway.adapterin.configuration.GatewayRuntimeProperties
import hs.kr.entrydsm.gateway.adapterin.error.GatewayErrorResponseWriter

import org.springframework.cloud.gateway.filter.GatewayFilterChain
import org.springframework.cloud.gateway.filter.GlobalFilter
import org.springframework.cloud.gateway.route.Route
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils
import org.springframework.core.Ordered
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

@Component
class GatewayCircuitBreakerGlobalFilter(
    properties: GatewayRuntimeProperties,
    private val metrics: GatewayCircuitBreakerMetrics,
) : GlobalFilter, Ordered {
    private val failureThreshold = properties.resilience.failureThreshold
    private val openDurationMillis = properties.resilience.openDurationSeconds * 1_000
    private val openDurationSeconds = properties.resilience.openDurationSeconds
    private val states = ConcurrentHashMap<String, CircuitState>()

    override fun filter(exchange: ServerWebExchange, chain: GatewayFilterChain): Mono<Void> {
        val routeId = exchange.getAttribute<Route>(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR)?.id ?: return chain.filter(exchange)
        val state = states.computeIfAbsent(routeId) { CircuitState() }
        if (!state.tryAcquire(openDurationMillis)) {
            metrics.recordRejected(routeId)
            exchange.response.headers.add("Retry-After", openDurationSeconds.toString())
            return GatewayErrorResponseWriter.write(exchange, HttpStatus.SERVICE_UNAVAILABLE, "CIRCUIT_OPEN")
        }
        return chain.filter(exchange)
            .doOnSuccess {
                val failed = exchange.response.statusCode?.is5xxServerError == true
                state.record(failed, failureThreshold, metrics, routeId)
            }
            .doOnError {
                state.record(true, failureThreshold, metrics, routeId)
            }
    }

    override fun getOrder(): Int = Ordered.LOWEST_PRECEDENCE - 100

    private class CircuitState {
        private val failures = AtomicInteger()
        private val openedAt = AtomicLong(0)

        fun tryAcquire(openDurationMillis: Long): Boolean {
            val opened = openedAt.get()
            if (opened == 0L) {
                return true
            }
            if (System.currentTimeMillis() - opened < openDurationMillis) {
                return false
            }
            failures.set(0)
            return openedAt.compareAndSet(opened, 0L)
        }

        fun record(
            failed: Boolean,
            failureThreshold: Int,
            metrics: GatewayCircuitBreakerMetrics,
            routeId: String,
        ) {
            if (failed && failures.incrementAndGet() >= failureThreshold) {
                metrics.recordFailure(routeId)
                if (openedAt.compareAndSet(0L, System.currentTimeMillis())) {
                    metrics.recordOpened(routeId)
                }
            } else if (!failed) {
                metrics.recordSuccess(routeId)
                failures.set(0)
                openedAt.set(0)
            }
        }
    }
}
