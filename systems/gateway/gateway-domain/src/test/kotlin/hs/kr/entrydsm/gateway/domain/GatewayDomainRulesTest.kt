package hs.kr.entrydsm.gateway.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class GatewayDomainRulesTest {
    @Test
    fun validatesTraceIdAndDefinesAllServices() {
        assertEquals("trace-01", TraceId.from("trace-01").value)
        assertEquals(GatewayService.entries.size, GatewayService.entries.map { it.routeId }.toSet().size)
        assertEquals(GatewayService.entries.size, GatewayService.entries.map { it.pathPrefix }.toSet().size)
        assertEquals(
            mapOf(
                "identity" to (GatewayDownstream.IDENTITY to "/api/identity"),
                "application" to (GatewayDownstream.APPLICATION to "/api/application"),
                "evaluation" to (GatewayDownstream.APPLICATION to "/api/evaluation"),
                "admin" to (GatewayDownstream.ADMIN to "/api/v11/admin"),
                "notification" to (GatewayDownstream.NOTIFICATION to "/api/notification"),
                "observability" to (GatewayDownstream.OBSERVABILITY to "/api/monitor"),
                "configuration" to (GatewayDownstream.CONFIGURATION to "/api/document"),
                "schedule" to (GatewayDownstream.CONFIGURATION to "/api/schedule"),
            ),
            GatewayService.entries.associate { it.routeId to (it.downstreamKey to it.pathPrefix) },
        )
        GatewayService.validateDefinitions()
        assertThrows(IllegalArgumentException::class.java) { TraceId.from("trace id") }
    }
}
