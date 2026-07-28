package hs.kr.entrydsm.gateway.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class GatewayDomainRulesTest {
    @Test
    fun validatesTraceIdAndDefinesAllServices() {
        assertEquals("trace-01", TraceId.from("trace-01").value)
        assertEquals(6, GatewayService.entries.size)
        assertThrows(IllegalArgumentException::class.java) { TraceId.from("trace id") }
    }
}
