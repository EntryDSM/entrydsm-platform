package hs.kr.entrydsm.gateway.adapterin.filter

import com.fasterxml.jackson.databind.ObjectMapper
import hs.kr.entrydsm.gateway.adapterin.configuration.GatewayRuntimeProperties
import hs.kr.entrydsm.gateway.adapterin.error.GatewayErrorResponseWriter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.cloud.gateway.filter.GatewayFilterChain
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import reactor.core.publisher.Mono

class GatewayCorsGlobalFilterTest {
    @Test
    fun acceptsCaseInsensitivePreflightHeadersAndExposesTraceId() {
        val exchange = MockServerWebExchange.from(
            MockServerHttpRequest.options("/api/identity/users")
                .header("Origin", "http://localhost:3000")
                .header("Access-Control-Request-Method", "post")
                .header("Access-Control-Request-Headers", "authorization,content-type")
                .build(),
        )

        filter().filter(exchange, GatewayFilterChain { Mono.empty() }).block()

        assertEquals("http://localhost:3000", exchange.response.headers.getFirst("Access-Control-Allow-Origin"))
        assertEquals("X-Trace-Id", exchange.response.headers.getFirst("Access-Control-Expose-Headers"))
        assertTrue(exchange.response.headers.getFirst("Vary")?.contains("Origin") == true)
    }

    @Test
    fun rejectsPreflightFromDisallowedOrigin() {
        val exchange = exchange(
            origin = "https://evil.example",
            requestedMethod = "GET",
            requestedHeaders = "Authorization",
        )
        var chainCalled = false

        filter().filter(exchange, GatewayFilterChain {
            chainCalled = true
            Mono.empty()
        }).block()

        assertEquals(403, exchange.response.statusCode?.value())
        assertFalse(chainCalled)
        assertEquals(null, exchange.response.headers.getFirst("Access-Control-Allow-Origin"))
    }

    @Test
    fun rejectsUnsupportedPreflightMethod() {
        val exchange = exchange(
            origin = "http://localhost:3000",
            requestedMethod = "TRACE",
            requestedHeaders = "Authorization",
        )

        filter().filter(exchange, GatewayFilterChain { Mono.empty() }).block()

        assertEquals(403, exchange.response.statusCode?.value())
        assertEquals(null, exchange.response.headers.getFirst("Access-Control-Allow-Origin"))
        assertEquals(null, exchange.response.headers.getFirst("Access-Control-Allow-Methods"))
    }

    @Test
    fun rejectsUnsupportedPreflightHeader() {
        val exchange = exchange(
            origin = "http://localhost:3000",
            requestedMethod = "GET",
            requestedHeaders = "X-Not-Allowed",
        )

        filter().filter(exchange, GatewayFilterChain { Mono.empty() }).block()

        assertEquals(403, exchange.response.statusCode?.value())
        assertEquals(null, exchange.response.headers.getFirst("Access-Control-Allow-Origin"))
        assertEquals(null, exchange.response.headers.getFirst("Access-Control-Allow-Headers"))
    }

    @Test
    fun passesNonPreflightDisallowedOriginWithoutCorsPermission() {
        val exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/api/identity/users")
                .header("Origin", "https://evil.example")
                .build(),
        )
        var chainCalled = false

        filter().filter(exchange, GatewayFilterChain {
            chainCalled = true
            Mono.empty()
        }).block()

        assertTrue(chainCalled)
        assertEquals(null, exchange.response.headers.getFirst("Access-Control-Allow-Origin"))
    }

    @Test
    fun passesOptionsWithoutPreflightHeadersThroughChain() {
        val exchange = MockServerWebExchange.from(
            MockServerHttpRequest.options("/api/identity/users")
                .header("Origin", "http://localhost:3000")
                .build(),
        )
        var chainCalled = false

        filter().filter(exchange, GatewayFilterChain {
            chainCalled = true
            Mono.empty()
        }).block()

        assertTrue(chainCalled)
        assertEquals("http://localhost:3000", exchange.response.headers.getFirst("Access-Control-Allow-Origin"))
        assertEquals(null, exchange.response.headers.getFirst("Access-Control-Allow-Methods"))
    }

    private fun exchange(origin: String, requestedMethod: String, requestedHeaders: String) =
        MockServerWebExchange.from(
            MockServerHttpRequest.options("/api/identity/users")
                .header("Origin", origin)
                .header("Access-Control-Request-Method", requestedMethod)
                .header("Access-Control-Request-Headers", requestedHeaders)
                .build(),
        )

    private fun filter() = GatewayCorsGlobalFilter(
        GatewayRuntimeProperties(),
        GatewayErrorResponseWriter(ObjectMapper()),
    )
}
