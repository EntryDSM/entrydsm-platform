package hs.kr.entrydsm.observability.application

import hs.kr.entrydsm.observability.application.port.out.ServerLogPage
import hs.kr.entrydsm.observability.application.port.out.ServerLogStorePort
import hs.kr.entrydsm.observability.application.port.out.StatusFilter
import hs.kr.entrydsm.observability.domain.enum.ErrorCode
import hs.kr.entrydsm.observability.domain.enum.ServiceName
import hs.kr.entrydsm.observability.domain.exception.MonitorDomainException
import hs.kr.entrydsm.observability.domain.model.Cursor
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ServerLogQueryServiceTest {
    private val clock = Clock.fixed(Instant.parse("2026-07-28T14:00:00Z"), ZoneOffset.UTC)
    private var lastStatusFilter: StatusFilter? = null
    private var lastFrom: Instant? = null
    private var lastTo: Instant? = null
    private val service = ServerLogQueryService(
        object : ServerLogStorePort {
            override fun list(
                from: Instant,
                to: Instant,
                service: ServiceName?,
                status: StatusFilter?,
                cursor: Cursor?,
                size: Int,
            ): ServerLogPage {
                lastStatusFilter = status
                lastFrom = from
                lastTo = to
                return ServerLogPage(0, emptyList(), null, false)
            }
        },
        clock,
    )

    @Test
    fun parsesStatusClassShorthand() {
        service.getLogs(ServiceName.APPLICATION, "5xx", null, null, 20, null)
        assertEquals(StatusFilter.StatusClass('5'), lastStatusFilter)
    }

    @Test
    fun parsesExactStatusCode() {
        service.getLogs(null, "500", null, null, 20, null)
        assertEquals(StatusFilter.Exact(500), lastStatusFilter)
    }

    @Test
    fun rejectsMalformedStatus() {
        assertThrows(MonitorDomainException::class.java) {
            service.getLogs(null, "not-a-status", null, null, 20, null)
        }
    }

    @Test
    fun rejectsStatusOutsideValidRange() {
        listOf("0xx", "6xx", "99", "600", "-1").forEach { status ->
            val exception = assertThrows(MonitorDomainException::class.java) {
                service.getLogs(null, status, null, null, 20, null)
            }
            assertEquals(ErrorCode.INVALID_PAYLOAD, exception.errorCode)
        }
    }

    @Test
    fun defaultLookbackIsRelativeToRequestedTo() {
        val to = Instant.parse("2026-07-27T00:00:00Z")

        service.getLogs(null, null, null, to, 20, null)

        assertEquals(Instant.parse("2026-07-26T23:00:00Z"), lastFrom)
        assertEquals(to, lastTo)
    }

    @Test
    fun rejectsRangeOverSevenDays() {
        assertThrows(MonitorDomainException::class.java) {
            service.getLogs(null, null, Instant.parse("2026-07-01T00:00:00Z"), Instant.parse("2026-07-28T00:00:00Z"), 20, null)
        }
    }
}
