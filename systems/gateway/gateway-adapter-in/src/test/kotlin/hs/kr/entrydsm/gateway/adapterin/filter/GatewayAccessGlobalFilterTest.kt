package hs.kr.entrydsm.gateway.adapterin.filter

import hs.kr.entrydsm.gateway.adapterin.configuration.GatewayServiceProperties
import hs.kr.entrydsm.gateway.adapterin.error.GatewayErrorResponseWriter
import java.net.InetSocketAddress
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.springframework.cloud.gateway.filter.GatewayFilterChain
import org.springframework.http.HttpHeaders
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import reactor.core.publisher.Mono
import tools.jackson.databind.json.JsonMapper

class GatewayAccessGlobalFilterTest {
    private val filter = GatewayAccessGlobalFilter(
        GatewayServiceProperties(),
        GatewayErrorResponseWriter(JsonMapper.builder().build()),
        JsonMapper.builder().build(),
    )

    @Test
    fun takesRightmostExternalForwardedForEntryWhenPeerIsInternalProxy() {
        val headers = forwardedHeaders(
            peer = "10.0.0.5",
            forwardedFor = "6.6.6.6, 203.0.113.9, 10.0.0.7",
            spoofedRealIp = "6.6.6.6",
        )

        assertEquals("203.0.113.9", headers.getFirst("X-Real-IP"))
        assertNull(headers.getFirst("X-Forwarded-For"))
    }

    @Test
    fun ignoresForwardedForFromPublicPeer() {
        val headers = forwardedHeaders(peer = "203.0.113.9", forwardedFor = "6.6.6.6")

        assertEquals("203.0.113.9", headers.getFirst("X-Real-IP"))
    }

    @Test
    fun fallsBackToInternalPeerWithoutForwardedFor() {
        assertEquals("10.0.0.5", forwardedHeaders(peer = "10.0.0.5", forwardedFor = null).getFirst("X-Real-IP"))
    }

    private fun forwardedHeaders(peer: String, forwardedFor: String?, spoofedRealIp: String? = null): HttpHeaders {
        val request = MockServerHttpRequest.post("/api/monitor/v11/collect/session")
            .remoteAddress(InetSocketAddress(peer, 443))
            .apply {
                forwardedFor?.let { header("X-Forwarded-For", it) }
                spoofedRealIp?.let { header("X-Real-IP", it) }
            }
            .build()
        var forwarded: HttpHeaders? = null

        filter.filter(MockServerWebExchange.from(request), GatewayFilterChain { exchange ->
            forwarded = exchange.request.headers
            Mono.empty()
        }).block()

        return checkNotNull(forwarded)
    }
}
