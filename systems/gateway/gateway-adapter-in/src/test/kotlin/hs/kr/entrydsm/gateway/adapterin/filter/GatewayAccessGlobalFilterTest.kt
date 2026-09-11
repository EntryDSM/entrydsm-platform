package hs.kr.entrydsm.gateway.adapterin.filter

import hs.kr.entrydsm.gateway.adapterin.configuration.GatewayServiceProperties
import hs.kr.entrydsm.gateway.adapterin.error.GatewayErrorResponseWriter
import java.net.InetSocketAddress
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.cloud.gateway.filter.GatewayFilterChain
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
        val clientIp = forwardedClientIp(
            peer = "10.0.0.5",
            forwardedFor = "6.6.6.6, 203.0.113.9, 10.0.0.7",
            spoofedRealIp = "6.6.6.6",
        )

        assertEquals("203.0.113.9", clientIp)
    }

    @Test
    fun ignoresForwardedForFromPublicPeer() {
        assertEquals("203.0.113.9", forwardedClientIp(peer = "203.0.113.9", forwardedFor = "6.6.6.6"))
    }

    @Test
    fun fallsBackToInternalPeerWithoutForwardedFor() {
        assertEquals("10.0.0.5", forwardedClientIp(peer = "10.0.0.5", forwardedFor = null))
    }

    private fun forwardedClientIp(peer: String, forwardedFor: String?, spoofedRealIp: String? = null): String? {
        val request = MockServerHttpRequest.post("/api/monitor/v11/collect/session")
            .remoteAddress(InetSocketAddress(peer, 443))
            .apply {
                forwardedFor?.let { header("X-Forwarded-For", it) }
                spoofedRealIp?.let { header("X-Real-IP", it) }
            }
            .build()
        var forwarded: String? = null

        filter.filter(MockServerWebExchange.from(request), GatewayFilterChain { exchange ->
            forwarded = exchange.request.headers.getFirst("X-Real-IP")
            Mono.empty()
        }).block()

        return forwarded
    }
}
