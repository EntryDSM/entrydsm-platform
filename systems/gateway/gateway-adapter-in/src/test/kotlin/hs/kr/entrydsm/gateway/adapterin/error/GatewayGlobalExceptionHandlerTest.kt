package hs.kr.entrydsm.gateway.adapterin.error

import hs.kr.entrydsm.gateway.adapterin.filter.GatewayRequestTooLargeException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange

class GatewayGlobalExceptionHandlerTest {
    @Test
    fun mapsUnknownGatewayExceptionTo500WithTraceId() {
        val exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/api/identity/users")
                .header("X-Trace-Id", "handler-trace")
                .build(),
        )

        GatewayGlobalExceptionHandler().handle(exchange, IllegalStateException("gateway failure")).block()

        assertEquals(500, exchange.response.statusCode?.value())
        assertEquals("handler-trace", exchange.response.headers.getFirst("X-Trace-Id"))
    }

    @Test
    fun mapsOversizedRequestTo413() {
        val exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/identity/users").build())

        GatewayGlobalExceptionHandler().handle(exchange, GatewayRequestTooLargeException()).block()

        assertEquals(413, exchange.response.statusCode?.value())
    }
}
