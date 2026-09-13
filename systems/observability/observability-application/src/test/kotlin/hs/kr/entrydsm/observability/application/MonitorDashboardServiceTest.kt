package hs.kr.entrydsm.observability.application

import hs.kr.entrydsm.observability.application.port.out.ClientLogStorePort
import hs.kr.entrydsm.observability.application.port.out.HealthCheckPort
import hs.kr.entrydsm.observability.application.port.out.Round
import hs.kr.entrydsm.observability.application.port.out.ServiceHealthCheck
import hs.kr.entrydsm.observability.application.port.out.StorageUsage
import hs.kr.entrydsm.observability.domain.enum.DeviceType
import hs.kr.entrydsm.observability.domain.enum.LogLevel
import hs.kr.entrydsm.observability.domain.enum.ServiceName
import hs.kr.entrydsm.observability.domain.exception.MonitorDomainException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class MonitorDashboardServiceTest {
    private val clock = Clock.fixed(Instant.parse("2026-07-28T14:00:00Z"), ZoneOffset.UTC)
    private val round = Round("2026-1", Instant.parse("2026-07-01T00:00:00Z"), Instant.parse("2026-07-31T23:59:59Z"))

    private fun service(): MonitorDashboardService = MonitorDashboardService(
        sessionStorePort = FakeSessionStorePort(),
        healthCheckPort = HealthCheckPort { ServiceHealthCheck(responseTimeMs = 12, version = null, dependencies = emptyMap()) },
        clientLogStorePort = object : ClientLogStorePort by NotImplementedClientLogStorePort {
            override fun countByLevel(from: Instant, to: Instant) = mapOf(LogLevel.ERROR to 2L, LogLevel.WARN to 1L)
        },
        storageUsagePort = {
            StorageUsage(
                databaseUsedBytes = 0,
                databaseTotalBytes = null,
                bucketUsedBytes = 1024,
                bucketObjectCount = 1,
                measuredAt = Instant.now(clock),
            )
        },
        roundPort = { round },
        clock = clock,
    )

    @Test
    fun buildsSnapshotWithDeviceRatiosAndLogCounts() {
        val result = service().getSnapshot(null)

        assertEquals(round.name, result.period.round)
        assertEquals(2L, result.clientLog.errorCount)
        assertEquals(1L, result.clientLog.warnCount)
        assertEquals(1024L, result.resource.bucketUsedBytes)
        val android = result.traffic.devices.first { it.type == DeviceType.ANDROID }
        assertEquals(0.5, android.ratio, 0.0001)
        val total = result.services.items.first { it.service == "TOTAL" }
        assertEquals(1, total.activeUsers)
    }

    @Test
    fun rejectsUnknownRound() {
        assertThrows(MonitorDomainException::class.java) {
            service().getSnapshot("not-a-real-round")
        }
    }

    private class FakeSessionStorePort : hs.kr.entrydsm.observability.application.port.out.SessionStorePort {
        override fun enter(sessionId: String, service: ServiceName, deviceType: DeviceType, now: Instant) = Unit
        override fun heartbeat(sessionId: String, service: ServiceName, now: Instant) = true
        override fun leave(sessionId: String, service: ServiceName, now: Instant) = true
        override fun concurrentUsers(service: ServiceName?, now: Instant, windowSeconds: Long) = 1
        override fun totalVisitors() = 2L
        override fun avgSessionDurationSeconds() = 120L
        override fun deviceBreakdown(): Map<DeviceType, Long> = mapOf(DeviceType.ANDROID to 1L, DeviceType.IOS to 1L)
        override fun sampleConcurrency(now: Instant, windowSeconds: Long) = Unit
        override fun concurrentMax() = 5
        override fun concurrentAvg() = 2
    }

    private object NotImplementedClientLogStorePort : ClientLogStorePort {
        override fun record(input: hs.kr.entrydsm.observability.application.port.out.ClientLogInput) =
            throw UnsupportedOperationException()
        override fun countByLevel(from: Instant, to: Instant): Map<LogLevel, Long> = throw UnsupportedOperationException()
        override fun list(
            from: Instant,
            to: Instant,
            levels: Set<LogLevel>?,
            cursor: hs.kr.entrydsm.observability.domain.model.Cursor?,
            size: Int,
        ) = throw UnsupportedOperationException()
    }
}
