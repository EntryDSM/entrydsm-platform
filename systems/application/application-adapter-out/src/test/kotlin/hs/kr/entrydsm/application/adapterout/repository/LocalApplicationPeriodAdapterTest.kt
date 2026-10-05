package hs.kr.entrydsm.application.adapterout.repository

import hs.kr.entrydsm.application.administration.domain.schedule.Schedule
import hs.kr.entrydsm.application.administration.domain.schedule.port.out.ScheduleRepository
import hs.kr.entrydsm.application.application.exception.ApplicationPeriodLookupFailedException
import java.lang.reflect.Proxy
import java.time.Instant
import java.time.LocalDateTime
import org.junit.Assert.*
import org.junit.Test

class LocalApplicationPeriodAdapterTest {
    @Test
    fun koreanSchedulePreservesInclusiveBoundariesWithoutRpc() {
        val schedule = Schedule(1, "원서 접수", LocalDateTime.of(2026, 9, 19, 9, 0), LocalDateTime.of(2026, 10, 22, 17, 0))
        val adapter = adapter { schedule }
        assertEquals(Instant.parse("2026-09-19T00:00:00Z"), adapter.read()?.start)
        assertEquals(Instant.parse("2026-10-22T08:00:00Z"), adapter.read()?.endInclusive)
        assertTrue(requireNotNull(adapter.read()).contains(Instant.parse("2026-10-22T08:00:00Z")))
    }

    @Test
    fun missingScheduleAndLookupFailureRemainDifferent() {
        assertNull(adapter { null }.read())
        assertThrows(ApplicationPeriodLookupFailedException::class.java) {
            adapter { error("DB 장애") }.read()
        }
    }

    private fun adapter(read: () -> Schedule?) = LocalApplicationPeriodAdapter(
        Proxy.newProxyInstance(javaClass.classLoader, arrayOf(ScheduleRepository::class.java)) { _, method, args ->
            assertEquals("findByTitle", method.name)
            assertEquals("원서 접수", args[0])
            read()
        } as ScheduleRepository,
    )
}
