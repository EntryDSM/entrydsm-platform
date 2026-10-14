package hs.kr.entrydsm.application.administration.application

import hs.kr.entrydsm.admin.domain.port.out.ApplicantRepository
import hs.kr.entrydsm.application.administration.domain.schedule.Schedule
import hs.kr.entrydsm.application.administration.domain.schedule.port.out.ScheduleRepository
import java.lang.reflect.Proxy
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FinalScreeningSchedulerTest {
    private val announcement = LocalDateTime.of(2026, 11, 4, 10, 0)
    private val announcementInstant = Instant.parse("2026-11-04T01:00:00Z")

    @Test
    fun `최종 발표 시각 전에는 알리지 않고 시각이 되면 한 번만 알린다`() {
        val schedules = Schedules()
        val published = mutableListOf<Instant>()
        val applicants = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(ApplicantRepository::class.java)) { _, method, args ->
            assertEquals("publishFinalResults", method.name)
            published += args[0] as Instant
            Unit
        } as ApplicantRepository

        FinalScreeningScheduler(schedules, applicants, Clock.fixed(announcementInstant.minusNanos(1), ZoneOffset.UTC)).publishDue()
        assertTrue(published.isEmpty())
        val scheduler = FinalScreeningScheduler(schedules, applicants, Clock.fixed(announcementInstant, ZoneOffset.UTC))
        scheduler.publishDue()
        scheduler.publishDue()

        assertEquals(listOf(announcementInstant), published)
        assertEquals("최종 합격 발표", schedules.lastTitle)
    }

    private inner class Schedules : ScheduleRepository {
        var processed = false
        var lastTitle: String? = null
        override fun claimFinalScreening(title: String, now: LocalDateTime): Boolean {
            lastTitle = title
            if (now.isBefore(announcement) || processed) return false
            processed = true
            return true
        }
        override fun claimFirstScreening(title: String, now: LocalDateTime): Boolean = error("unexpected claim")
        override fun findByTitle(title: String): Schedule? = error("unexpected query")
        override fun findByStartAtBetween(startAt: LocalDateTime, endAt: LocalDateTime): List<Schedule> = error("unexpected query")
        override fun save(schedule: Schedule): Schedule = error("unexpected save")
    }
}
