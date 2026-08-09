package hs.kr.entrydsm.observability.application

import hs.kr.entrydsm.observability.application.port.out.ClientLogEntry
import hs.kr.entrydsm.observability.application.port.out.ClientLogInput
import hs.kr.entrydsm.observability.application.port.out.ClientLogPage
import hs.kr.entrydsm.observability.application.port.out.ClientLogStorePort
import hs.kr.entrydsm.observability.domain.enum.LogLevel
import hs.kr.entrydsm.observability.domain.exception.MonitorDomainException
import hs.kr.entrydsm.observability.domain.model.Cursor
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ClientLogQueryServiceTest {
    private val clock = Clock.fixed(Instant.parse("2026-07-28T14:00:00Z"), ZoneOffset.UTC)
    private val service = ClientLogQueryService(FakeClientLogStorePort(), clock)

    @Test
    fun defaultsToOneHourLookbackAndClampsSize() {
        val result = service.getLogs(null, null, null, 500, null)

        assertEquals(1, result.totalCount)
    }

    @Test
    fun rejectsRangeOverSevenDays() {
        assertThrows(MonitorDomainException::class.java) {
            service.getLogs(null, Instant.parse("2026-07-01T00:00:00Z"), Instant.parse("2026-07-28T00:00:00Z"), 20, null)
        }
    }

    private class FakeClientLogStorePort : ClientLogStorePort {
        override fun record(input: ClientLogInput) = Unit
        override fun countByLevel(from: Instant, to: Instant): Map<LogLevel, Long> = emptyMap()
        override fun list(from: Instant, to: Instant, levels: Set<LogLevel>?, cursor: Cursor?, size: Int): ClientLogPage =
            ClientLogPage(1, 1, 0, emptyList<ClientLogEntry>(), null, false)
    }
}
