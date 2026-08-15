package hs.kr.entrydsm.gateway.adapterin.trace

import hs.kr.entrydsm.gateway.domain.TraceId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.slf4j.MDC
import org.springframework.cloud.gateway.filter.GatewayFilterChain
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono

class TraceIdGlobalFilterTest {
    @Test
    fun createsTraceIdAndForwardsItToDownstreamRequest() {
        val exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/identity/me").build())
        var downstreamExchange: ServerWebExchange? = null

        TraceIdGlobalFilter().filter(exchange, GatewayFilterChain {
            downstreamExchange = it
            Mono.empty()
        }).block()

        val traceId = exchange.response.headers.getFirst(TraceId.HEADER_NAME)
        assertNotNull(traceId)
        assertEquals(traceId, downstreamExchange?.request?.headers?.getFirst(TraceId.HEADER_NAME))
    }

    @Test
    fun preservesExistingTraceIdAndReactorContext() {
        val exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/identity/me")
                .header(TraceId.HEADER_NAME, "trace-context")
                .build(),
        )
        var contextValue: String? = null

        TraceIdGlobalFilter().filter(exchange, GatewayFilterChain {
            Mono.deferContextual { context ->
                contextValue = context.get("gateway.trace-id")
                Mono.empty()
            }
        }).block()

        assertEquals("trace-context", exchange.response.headers.getFirst(TraceId.HEADER_NAME))
        assertEquals("trace-context", contextValue)
    }

    @Test
    fun keepsTraceIdInMdcDuringReactorSignals() {
        val exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/identity/me")
                .header(TraceId.HEADER_NAME, "mdc-trace")
                .build(),
        )
        val configuration = TraceMdcConfiguration()
        configuration.register()
        try {
            TraceIdGlobalFilter().filter(exchange, GatewayFilterChain {
                Mono.just(Unit).doOnEach { signal ->
                    if (signal.isOnNext) {
                        assertEquals("mdc-trace", MDC.get(TraceId.HEADER_NAME))
                    }
                }.then()
            }).block()
        } finally {
            configuration.unregister()
        }
    }

    @Test
    fun keepsTraceIdInMdcDuringSubscription() {
        val exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/identity/me")
                .header(TraceId.HEADER_NAME, "subscribe-trace")
                .build(),
        )
        val configuration = TraceMdcConfiguration()
        configuration.register()
        try {
            TraceIdGlobalFilter().filter(exchange, GatewayFilterChain {
                Mono.just(Unit).doOnSubscribe {
                    assertEquals("subscribe-trace", MDC.get(TraceId.HEADER_NAME))
                }.then()
            }).block()
        } finally {
            configuration.unregister()
        }
    }
}
