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
    private val store = FakeClientLogStorePort()
    private val service = ClientLogQueryService(store, clock)

    @Test
    fun defaultsToOneHourLookbackAndClampsSize() {
        val result = service.getLogs(null, null, null, 500, null)

        assertEquals(1, result.totalCount)
        assertEquals(Instant.parse("2026-07-28T13:00:00Z"), store.lastFrom)
        assertEquals(Instant.parse("2026-07-28T14:00:00Z"), store.lastTo)
        assertEquals(100, store.lastSize)
    }

    @Test
    fun defaultLookbackIsRelativeToRequestedTo() {
        val to = Instant.parse("2026-07-27T00:00:00Z")

        service.getLogs(null, null, to, 20, null)

        assertEquals(Instant.parse("2026-07-26T23:00:00Z"), store.lastFrom)
        assertEquals(to, store.lastTo)
    }

    @Test
    fun rejectsRangeOverSevenDays() {
        assertThrows(MonitorDomainException::class.java) {
            service.getLogs(null, Instant.parse("2026-07-01T00:00:00Z"), Instant.parse("2026-07-28T00:00:00Z"), 20, null)
        }
    }

    private class FakeClientLogStorePort : ClientLogStorePort {
        var lastFrom: Instant? = null
        var lastTo: Instant? = null
        var lastSize: Int? = null

        override fun record(input: ClientLogInput) = Unit
        override fun countByLevel(from: Instant, to: Instant): Map<LogLevel, Long> = emptyMap()
        override fun list(from: Instant, to: Instant, levels: Set<LogLevel>?, cursor: Cursor?, size: Int): ClientLogPage {
            lastFrom = from
            lastTo = to
            lastSize = size
            return ClientLogPage(1, 1, 0, emptyList<ClientLogEntry>(), null, false)
        }
    }
}
