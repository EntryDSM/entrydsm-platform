package hs.kr.entrydsm.observability.adapterin.web

import org.junit.Assert.assertEquals
import org.junit.Test

class ClientIpResolverTest {
    private val resolver = ClientIpResolver()

    @Test
    fun usesClientIpForwardedByGateway() {
        assertEquals("203.0.113.9", resolver.resolve("172.18.0.5", "203.0.113.9"))
    }

    @Test
    fun fallsBackToRemoteAddrWithoutGatewayHeader() {
        assertEquals("10.0.0.1", resolver.resolve("10.0.0.1", null))
        assertEquals("10.0.0.1", resolver.resolve("10.0.0.1", " "))
    }

    @Test
    fun fallsBackToUnknownWhenNothingIsKnown() {
        assertEquals("unknown", resolver.resolve(null, null))
    }
}
