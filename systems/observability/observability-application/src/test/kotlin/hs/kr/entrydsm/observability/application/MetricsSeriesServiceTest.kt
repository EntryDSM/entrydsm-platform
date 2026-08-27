package hs.kr.entrydsm.observability.application

import hs.kr.entrydsm.observability.application.port.out.MetricsStorePort
import hs.kr.entrydsm.observability.domain.enum.MetricType
import hs.kr.entrydsm.observability.domain.exception.MonitorDomainException
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class MetricsSeriesServiceTest {
    private val clock = Clock.fixed(Instant.parse("2026-07-28T14:00:00Z"), ZoneOffset.UTC)
    private val service = MetricsSeriesService(FakeMetricsStorePort(), clock)

    @Test
    fun buildsZeroFilledPointsForApiRequestAndVisitorFromStore() {
        val from = Instant.parse("2026-07-28T00:00:00Z")
        val to = Instant.parse("2026-07-28T02:00:00Z")

        val result = service.getSeries(listOf(MetricType.API_REQUEST, MetricType.VISITOR), from, to, "1h")

        assertEquals(2, result.series.first { it.metric == MetricType.API_REQUEST }.points.size)
        assertEquals(0L, result.series.first { it.metric == MetricType.API_REQUEST }.points[0].v)
        assertEquals(7L, result.series.first { it.metric == MetricType.VISITOR }.points[0].v)
    }

    @Test
    fun rejectsUnknownMetric() {
        assertThrows(MonitorDomainException::class.java) {
            service.getSeries(emptyList(), null, null, null)
        }
    }

    @Test
    fun rejectsInvertedTimeRange() {
        assertThrows(MonitorDomainException::class.java) {
            service.getSeries(
                listOf(MetricType.VISITOR),
                Instant.parse("2026-07-28T10:00:00Z"),
                Instant.parse("2026-07-28T09:00:00Z"),
                "1h",
            )
        }
    }

    @Test
    fun rejectsTooManyBuckets() {
        assertThrows(MonitorDomainException::class.java) {
            service.getSeries(
                listOf(MetricType.VISITOR),
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-10T00:00:00Z"),
                "5m",
            )
        }
    }

    private class FakeMetricsStorePort : MetricsStorePort {
        override fun recordVisitor(sessionId: String, at: Instant) = Unit
        override fun visitorCount(from: Instant, to: Instant): Long =
            if (Duration.between(from, to) == Duration.ofHours(1)) 7L else 0L
    }
}
