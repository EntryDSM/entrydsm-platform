package hs.kr.entrydsm.gateway.adapterin.filter

import hs.kr.entrydsm.gateway.adapterin.configuration.GatewayRuntimeProperties
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

        GatewayCorsGlobalFilter(GatewayRuntimeProperties()).filter(exchange, GatewayFilterChain { Mono.empty() }).block()

        assertTrue(exchange.response.headers.getFirst("Access-Control-Allow-Origin") == "http://localhost:3000")
        assertTrue(exchange.response.headers.getFirst("Access-Control-Expose-Headers") == "X-Trace-Id")
        assertTrue(exchange.response.headers.getFirst("Vary")?.contains("Origin") == true)
    }
}
