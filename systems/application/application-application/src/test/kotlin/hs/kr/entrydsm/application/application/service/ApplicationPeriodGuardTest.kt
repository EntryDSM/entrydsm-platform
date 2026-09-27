package hs.kr.entrydsm.application.application.service

import hs.kr.entrydsm.application.application.exception.ApplicationPeriodClosedException
import hs.kr.entrydsm.application.application.port.out.ApplicationPeriodReader
import java.time.Instant
import org.junit.Assert.assertThrows
import org.junit.Test

class ApplicationPeriodGuardTest {
    private val startAt = Instant.parse("2026-09-19T00:00:00Z")
    private val endAt = Instant.parse("2026-10-22T08:00:00Z")
    private val period = ApplicationPeriodReader { startAt..endAt }

    @Test
    fun startAndEndInstantsAreInsideThePeriod() {
        period.requireOpen(now = startAt)
        period.requireOpen(now = endAt)
    }

    @Test
    fun beforeStartAfterEndOrWithoutScheduleIsClosed() {
        listOf(startAt.minusMillis(1), endAt.plusMillis(1)).forEach { now ->
            assertThrows(ApplicationPeriodClosedException::class.java) { period.requireOpen(now) }
        }
        assertThrows(ApplicationPeriodClosedException::class.java) {
            ApplicationPeriodReader { null }.requireOpen(now = startAt)
        }
    }
}
