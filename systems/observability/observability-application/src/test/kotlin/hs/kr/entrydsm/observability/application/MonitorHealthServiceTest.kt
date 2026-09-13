package hs.kr.entrydsm.observability.application

import hs.kr.entrydsm.observability.application.port.out.HealthCheckPort
import hs.kr.entrydsm.observability.application.port.out.ServiceHealthCheck
import hs.kr.entrydsm.observability.domain.enum.ServiceName
import hs.kr.entrydsm.observability.domain.enum.ServiceStatus
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Test

class MonitorHealthServiceTest {
    private val clock = Clock.fixed(Instant.parse("2026-07-28T14:00:00Z"), ZoneOffset.UTC)

    @Test
    fun overallFollowsWorstServiceStatus() {
        val healthCheckPort = HealthCheckPort { service ->
            if (service == ServiceName.APPLICATION) {
                ServiceHealthCheck(responseTimeMs = 1840, version = "1.4.0", dependencies = mapOf("s3" to false))
            } else {
                ServiceHealthCheck(responseTimeMs = 12, version = "1.4.2", dependencies = mapOf("postgres" to true))
            }
        }
        val service = MonitorHealthService(healthCheckPort, clock)

        val result = service.getHealth()

        assertEquals(ServiceStatus.DEGRADED, result.overall)
        val application = result.services.first { it.service == ServiceName.APPLICATION }
        assertEquals(ServiceStatus.DEGRADED, application.status)
        assertEquals(ServiceStatus.DOWN, application.dependencies.first().status)
    }
}
