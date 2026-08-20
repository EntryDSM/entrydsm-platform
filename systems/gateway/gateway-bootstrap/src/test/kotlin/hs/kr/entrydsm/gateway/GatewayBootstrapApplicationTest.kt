package hs.kr.entrydsm.gateway

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class GatewayBootstrapApplicationTest {
    @Test
    fun applicationHasTheGatewayIdentity() {
        assertEquals("hs.kr.entrydsm.gateway", GatewayBootstrapApplication::class.java.packageName)
    }
}
