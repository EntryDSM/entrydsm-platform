package hs.kr.entrydsm.observability.application

import hs.kr.entrydsm.observability.application.port.out.MetricsStorePort
import hs.kr.entrydsm.observability.application.port.out.RateLimitPort
import hs.kr.entrydsm.observability.application.port.out.SessionStorePort
import hs.kr.entrydsm.observability.domain.enum.DeviceType
import hs.kr.entrydsm.observability.domain.enum.ServiceName
import hs.kr.entrydsm.observability.domain.enum.SessionEventType
import hs.kr.entrydsm.observability.domain.exception.MonitorDomainException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SessionCollectionServiceTest {
    private val clock = Clock.fixed(Instant.parse("2026-07-28T14:00:00Z"), ZoneOffset.UTC)
    private val sessionStore = FakeSessionStorePort()

    @Test
    fun enterIssuesNewSessionId() {
        val service = SessionCollectionService(sessionStore, FakeRateLimitPort(true), FakeMetricsStorePort(), clock)

        val result = service.record(SessionEventType.ENTER, null, ServiceName.APPLICATION, "iPhone", "127.0.0.1")

        assertEquals(true, result.sessionId.startsWith("sess_"))
        assertEquals(15, result.heartbeatIntervalSeconds)
    }

    @Test
    fun heartbeatOnUnknownSessionThrowsSessionNotFound() {
        val service = SessionCollectionService(sessionStore, FakeRateLimitPort(true), FakeMetricsStorePort(), clock)

        assertThrows(MonitorDomainException::class.java) {
            service.record(SessionEventType.HEARTBEAT, "sess_unknown", ServiceName.APPLICATION, null, "127.0.0.1")
        }
    }

    @Test
    fun rateLimitExceededThrowsTooManyRequests() {
        val service = SessionCollectionService(sessionStore, FakeRateLimitPort(false), FakeMetricsStorePort(), clock)

        assertThrows(MonitorDomainException::class.java) {
            service.record(SessionEventType.ENTER, null, ServiceName.APPLICATION, null, "127.0.0.1")
        }
    }

    private class FakeSessionStorePort : SessionStorePort {
        private val active = mutableSetOf<String>()

        override fun enter(sessionId: String, service: ServiceName, deviceType: DeviceType, now: Instant) {
            active.add(sessionId)
        }

        override fun heartbeat(sessionId: String, service: ServiceName, now: Instant) = active.contains(sessionId)

        override fun leave(sessionId: String, service: ServiceName, now: Instant) = active.remove(sessionId)

        override fun concurrentUsers(service: ServiceName?, now: Instant, windowSeconds: Long) = active.size

        override fun totalVisitors() = active.size.toLong()

        override fun avgSessionDurationSeconds() = 0L

        override fun deviceBreakdown(): Map<DeviceType, Long> = emptyMap()

        override fun sampleConcurrency(now: Instant, windowSeconds: Long) = Unit

        override fun concurrentMax() = 0

        override fun concurrentAvg() = 0
    }

    private class FakeRateLimitPort(private val allow: Boolean) : RateLimitPort {
        override fun tryAcquire(key: String, limit: Long, windowSeconds: Long) = allow
    }

    private class FakeMetricsStorePort : MetricsStorePort {
        override fun recordVisitor(sessionId: String, at: Instant) = Unit
        override fun visitorCount(from: Instant, to: Instant) = 0L
    }
}
