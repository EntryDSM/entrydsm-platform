package hs.kr.entrydsm.gateway.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class GatewayDomainRulesTest {
    @Test
    fun validatesTraceIdAndDefinesAllServices() {
        assertEquals("trace-01", TraceId.from("trace-01").value)
        assertEquals(
            mapOf(
                "identity" to "/api/identity",
                "application" to "/api/application",
                "admin" to "/api/admin",
                "notification" to "/api/notification",
                "observability" to "/api/observability",
                "configuration" to "/api/configuration",
            ),
            GatewayService.entries.associate { it.routeId to it.pathPrefix },
        )
        assertThrows(IllegalArgumentException::class.java) { TraceId.from("trace id") }
    }
}
