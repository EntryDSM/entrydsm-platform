package hs.kr.entrydsm.gateway.adapterin.error

import com.fasterxml.jackson.databind.ObjectMapper
import hs.kr.entrydsm.gateway.adapterin.filter.GatewayRequestTooLargeException
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import org.springframework.core.io.buffer.DataBufferUtils
import java.nio.charset.StandardCharsets

class GatewayGlobalExceptionHandlerTest {
    @Test
    fun mapsUnknownGatewayExceptionTo500WithTraceId() {
        val exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/api/identity/users")
                .header("X-Trace-Id", "handler-trace")
                .build(),
        )

        exchange.response.headers.set("X-Trace-Id", "handler-trace")
        handler().handle(exchange, IllegalStateException("gateway failure")).block()

        assertEquals(500, exchange.response.statusCode?.value())
        assertEquals("handler-trace", exchange.response.headers.getFirst("X-Trace-Id"))
    }

    @Test
    fun mapsOversizedRequestTo413() {
        val exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/identity/users").build())

        handler().handle(exchange, GatewayRequestTooLargeException()).block()

        assertEquals(413, exchange.response.statusCode?.value())
    }

    @Test
    fun mapsInvalidTraceIdTo400WithoutEchoingTheInvalidValue() {
        val exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/api/identity/users")
                .header("X-Trace-Id", "invalid\"trace")
                .build(),
        )

        handler().handle(
            exchange,
            InvalidTraceIdException(IllegalArgumentException("invalid trace")),
        ).block()

        val body = responseBody(exchange)
        assertEquals(400, exchange.response.statusCode?.value())
        assertTrue(body.contains("\"error\":\"INVALID_TRACE_ID\""))
        assertTrue(!body.contains("invalid\\\"trace"))
    }

    @Test
    fun serializesErrorFieldsAsValidUtf8Json() {
        val exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/error").build())

        GatewayErrorResponseWriter(ObjectMapper()).write(
            exchange,
            org.springframework.http.HttpStatus.BAD_REQUEST,
            "quote\" slash\\ 한글",
        ).block()

        val json = ObjectMapper().readTree(responseBody(exchange))
        assertEquals("quote\" slash\\ 한글", json["error"].asText())
    }

    private fun responseBody(exchange: MockServerWebExchange): String =
        DataBufferUtils.join(exchange.response.body)
            .map { buffer ->
                try {
                    buffer.toString(StandardCharsets.UTF_8)
                } finally {
                    DataBufferUtils.release(buffer)
                }
            }
            .block()
            .orEmpty()

    private fun handler() = GatewayGlobalExceptionHandler(GatewayErrorResponseWriter(ObjectMapper()))
}
