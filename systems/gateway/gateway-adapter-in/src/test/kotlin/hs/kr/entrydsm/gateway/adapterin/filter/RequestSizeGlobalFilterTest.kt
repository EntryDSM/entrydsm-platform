package hs.kr.entrydsm.gateway.adapterin.filter

import hs.kr.entrydsm.gateway.adapterin.configuration.GatewayRuntimeProperties
import hs.kr.entrydsm.gateway.adapterin.error.GatewayErrorResponseWriter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.cloud.gateway.filter.GatewayFilterChain
import org.springframework.core.io.buffer.DefaultDataBufferFactory
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import tools.jackson.databind.json.JsonMapper

class RequestSizeGlobalFilterTest {
    @Test
    fun rejectsContentLengthAboveLimitBeforeCallingChain() {
        val exchange = MockServerWebExchange.from(
            MockServerHttpRequest.post("/upload")
                .header("Content-Length", "11")
                .build(),
        )
        var chainCalled = false

        filter(10).filter(exchange, chain {
            chainCalled = true
        }).block()

        assertEquals(413, exchange.response.statusCode?.value())
        assertFalse(chainCalled)
    }

    @Test
    fun rejectsChunkedBodyWhenAccumulatedBytesExceedLimit() {
        val first = DefaultDataBufferFactory.sharedInstance.wrap(ByteArray(6))
        val second = DefaultDataBufferFactory.sharedInstance.wrap(ByteArray(5))
        val exchange = MockServerWebExchange.from(
            MockServerHttpRequest.post("/upload")
                .body(Flux.just(first, second)),
        )

        assertThrows(GatewayRequestTooLargeException::class.java) {
            filter(10).filter(exchange, GatewayFilterChain { current ->
                current.request.body.then()
            }).block()
        }
    }

    @Test
    fun forwardsBodyThatExactlyMatchesLimit() {
        val body = DefaultDataBufferFactory.sharedInstance.wrap(ByteArray(10))
        val exchange = MockServerWebExchange.from(
            MockServerHttpRequest.post("/upload")
                .body(Flux.just(body)),
        )
        var chainCalled = false

        filter(10).filter(exchange, GatewayFilterChain { current ->
            chainCalled = true
            current.request.body.then()
        }).block()

        assertTrue(chainCalled)
        assertEquals(null, exchange.response.statusCode)
    }

    private fun properties(maxBodyBytes: Long) = GatewayRuntimeProperties(
        request = GatewayRuntimeProperties.Request(maxBodyBytes = maxBodyBytes),
    )

    private fun filter(maxBodyBytes: Long) = RequestSizeGlobalFilter(
        properties(maxBodyBytes),
        GatewayErrorResponseWriter(JsonMapper.builder().build()),
    )

    private fun chain(onCall: () -> Unit) = GatewayFilterChain {
        onCall()
        Mono.empty()
    }
}
