package hs.kr.entrydsm.gateway.adapterin.resilience

import com.fasterxml.jackson.databind.ObjectMapper
import hs.kr.entrydsm.gateway.adapterin.configuration.GatewayRuntimeProperties
import hs.kr.entrydsm.gateway.adapterin.error.GatewayErrorResponseWriter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.cloud.gateway.filter.GatewayFilterChain
import org.springframework.cloud.gateway.route.Route
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import reactor.core.publisher.Mono
import tools.jackson.databind.json.JsonMapper
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import java.net.URI

class GatewayCircuitBreakerGlobalFilterTest {
    @Test
    fun returns503WhenSharedStoreRejectsRoute() {
        val exchange = exchange()
        var chainCalled = false
        val store = FakeStateStore(GatewayCircuitPermit(allowed = false, halfOpen = false))

        filter(store).filter(exchange, GatewayFilterChain {
            chainCalled = true
            Mono.empty()
        }).block()

        assertEquals(503, exchange.response.statusCode?.value())
        assertEquals("CIRCUIT_OPEN", responseError(exchange))
        assertEquals("30", exchange.response.headers.getFirst("Retry-After"))
        assertFalse(chainCalled)
        assertFalse(store.recorded)
    }

    @Test
    fun recordsSuccessfulHalfOpenProbeAndAllowsOnlySharedDecision() {
        val exchange = exchange()
        val permit = GatewayCircuitPermit(allowed = true, halfOpen = true, permitId = "probe-1")
        val store = FakeStateStore(permit)

        filter(store).filter(exchange, GatewayFilterChain { Mono.empty() }).block()

        assertEquals(true, store.recordedHalfOpen)
        assertEquals("probe-1", store.recordedPermitId)
    }

    @Test
    fun doesNotApplyLocalCircuitBreakerPermissionAsSecondGate() {
        val exchange = exchange()
        val registry = CircuitBreakerRegistry.ofDefaults()
        val breaker = registry.circuitBreaker("identity")
        breaker.transitionToOpenState()
        val store = FakeStateStore(GatewayCircuitPermit(allowed = true, halfOpen = false))

        filter(store, registry).filter(exchange, GatewayFilterChain { Mono.empty() }).block()

        assertEquals(null, exchange.response.statusCode)
        assertTrue(store.recorded)
    }

    @Test
    fun recordsDownstream5xxAsFailure() {
        val exchange = exchange()
        val store = FakeStateStore(GatewayCircuitPermit(allowed = true, halfOpen = false))

        filter(store).filter(exchange, GatewayFilterChain {
            exchange.response.statusCode = org.springframework.http.HttpStatus.BAD_GATEWAY
            Mono.empty()
        }).block()

        assertTrue(store.recorded)
        assertTrue(store.recordedFailed)
    }

    @Test
    fun recordsChainFailureAndRethrowsOriginalError() {
        val exchange = exchange()
        val failure = IllegalStateException("downstream failed")
        val store = FakeStateStore(GatewayCircuitPermit(allowed = true, halfOpen = false))

        val thrown = assertThrows(IllegalStateException::class.java) {
            filter(store).filter(exchange, GatewayFilterChain { Mono.error(failure) }).block()
        }

        assertSame(failure, thrown)
        assertTrue(store.recorded)
        assertTrue(store.recordedFailed)
    }

    @Test
    fun doesNotTreatStateStoreRecordFailureAsDownstreamFailure() {
        val exchange = exchange()
        val store = FakeStateStore(
            GatewayCircuitPermit(allowed = true, halfOpen = false),
            recordError = IllegalStateException("store failed"),
        )

        filter(store).filter(exchange, GatewayFilterChain { Mono.empty() }).block()

        assertEquals(1, store.recordCalls)
        assertFalse(store.recordedFailed)
    }

    private fun filter(
        store: FakeStateStore,
        registry: CircuitBreakerRegistry = CircuitBreakerRegistry.ofDefaults(),
    ) = GatewayCircuitBreakerGlobalFilter(
        GatewayRuntimeProperties(),
        registry,
        store,
        GatewayErrorResponseWriter(JsonMapper.builder().build()),
    )

    private fun exchange(): MockServerWebExchange = MockServerWebExchange.from(
        MockServerHttpRequest.get("/api/identity/users").build(),
    ).also { exchange ->
        exchange.attributes[ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR] =
            Route.async().id("identity").uri(URI("http://identity")).predicate { true }.build()
    }

    private fun responseError(exchange: MockServerWebExchange): String =
        exchange.response.body
            .map { buffer -> buffer.toString(Charsets.UTF_8) }
            .reduce(String::plus)
            .block()
            ?.let { body -> ObjectMapper().readTree(body).path("error").asText() }
            .orEmpty()

    private class FakeStateStore(
        private val permit: GatewayCircuitPermit,
        private val recordError: Throwable? = null,
    ) : GatewayCircuitStateStore {
        var recorded = false
        var recordedHalfOpen = false
        var recordedPermitId: String? = null
        var recordedFailed = false
        var recordCalls = 0

        override fun tryAcquire(
            routeId: String,
            policy: GatewayRuntimeProperties.Resilience,
        ): Mono<GatewayCircuitPermit> = Mono.just(permit)

        override fun releaseHalfOpen(routeId: String, permitId: String?): Mono<Void> = Mono.empty()

        override fun record(
            routeId: String,
            failed: Boolean,
            halfOpen: Boolean,
            policy: GatewayRuntimeProperties.Resilience,
            permitId: String?,
        ): Mono<Void> {
            recorded = true
            recordCalls += 1
            recordedHalfOpen = halfOpen
            recordedPermitId = permitId
            recordedFailed = failed
            if (recordError != null) return Mono.error(recordError)
            return Mono.empty()
        }
    }
}
