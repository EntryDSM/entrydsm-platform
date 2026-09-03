package hs.kr.entrydsm.observability.adapterin.web

import org.junit.Assert.assertEquals
import org.junit.Test

class ClientIpResolverTest {

    @Test
    fun ignoresForwardedHeaderFromUntrustedPeer() {
        assertEquals("203.0.113.9", ClientIpResolver("10.0.0.1").resolve("203.0.113.9", "1.2.3.4"))
    }

    @Test
    fun usesForwardedHeaderFromTrustedProxy() {
        assertEquals("1.2.3.4", ClientIpResolver("10.0.0.1").resolve("10.0.0.1", "1.2.3.4"))
    }

    @Test
    fun takesRightmostNonProxyEntryOfForwardedChain() {
        val resolver = ClientIpResolver("10.0.0.1, 10.0.0.2")

        assertEquals("1.2.3.4", resolver.resolve("10.0.0.1", "9.9.9.9, 1.2.3.4, 10.0.0.2"))
    }

    @Test
    fun fallsBackToRemoteAddrWhenTrustedProxySendsNoHeader() {
        assertEquals("10.0.0.1", ClientIpResolver("10.0.0.1").resolve("10.0.0.1", null))
    }

    @Test
    fun trustsNoProxyByDefault() {
        assertEquals("10.0.0.1", ClientIpResolver("").resolve("10.0.0.1", "1.2.3.4"))
    }

    @Test
    fun fallsBackToUnknownWhenRemoteAddrIsMissing() {
        assertEquals("unknown", ClientIpResolver("").resolve(null, "1.2.3.4"))
    }
}
