package hs.kr.entrydsm.gateway.adapterin.error

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.cloud.gateway.filter.GatewayFilterChain
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import reactor.core.publisher.Mono
import java.net.ConnectException

class DownstreamFailureGlobalFilterTest {
    @Test
    fun mapsConnectionFailureTo502WithTraceId() {
        val exchange = exchange("failure-trace")

        DownstreamFailureGlobalFilter(GatewayGlobalExceptionHandler()).filter(exchange, GatewayFilterChain {
            Mono.error(ConnectException("downstream unavailable"))
        }).block()

        assertEquals(502, exchange.response.statusCode?.value())
        assertEquals("failure-trace", exchange.response.headers.getFirst("X-Trace-Id"))
    }

    @Test
    fun mapsReactorNettyTimeoutFailureTo504() {
        val exchange = exchange("timeout-trace")

        DownstreamFailureGlobalFilter(GatewayGlobalExceptionHandler()).filter(exchange, GatewayFilterChain {
            Mono.error(ReadTimeoutException())
        }).block()

        assertEquals(504, exchange.response.statusCode?.value())
        assertEquals("timeout-trace", exchange.response.headers.getFirst("X-Trace-Id"))
    }

    private fun exchange(traceId: String) = MockServerWebExchange.from(
        MockServerHttpRequest.get("/identity/me")
            .header("X-Trace-Id", traceId)
            .build(),
    )

    private class ReadTimeoutException : RuntimeException()
}
