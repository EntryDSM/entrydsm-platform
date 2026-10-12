package hs.kr.entrydsm.application.administration.application

import hs.kr.entrydsm.admin.domain.command.EvaluateScreeningCommand
import hs.kr.entrydsm.admin.domain.model.ScreeningResult
import hs.kr.entrydsm.admin.domain.port.`in`.EvaluateFirstScreeningUseCase
import hs.kr.entrydsm.application.administration.domain.schedule.Schedule
import hs.kr.entrydsm.application.administration.domain.schedule.port.out.ScheduleRepository
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import org.junit.Assert.*
import org.junit.Test

class FirstScreeningSchedulerTest {
    private val announcement = LocalDateTime.of(2026, 10, 30, 10, 0)
    private val announcementInstant = Instant.parse("2026-10-30T01:00:00Z")

    @Test
    fun `서울 발표시각 직전에는 산출하지 않고 정확한 시각에 저장 산출을 요청한다`() {
        val schedules = Schedules()
        val screening = Screening()
        scheduler(schedules, screening, announcementInstant.minusNanos(1)).evaluateDue()
        assertTrue(screening.commands.isEmpty())
        scheduler(schedules, screening, announcementInstant).evaluateDue()
        assertEquals(listOf(EvaluateScreeningCommand(dryRun = false)), screening.commands)
        assertEquals(announcement, schedules.lastNow)
        assertTrue(schedules.processed)
    }

    @Test
    fun `발표시각 후 재시작한 서비스도 미처리 일정을 산출한다`() {
        val schedules = Schedules()
        val screening = Screening()
        scheduler(schedules, screening, announcementInstant.plusSeconds(60)).evaluateDue()
        assertEquals(1, screening.commands.size)
    }

    @Test
    fun `반복 실행과 객체 재생성에도 저장된 처리상태로 중복 산출하지 않는다`() {
        val schedules = Schedules()
        val screening = Screening()
        val scheduler = scheduler(schedules, screening, announcementInstant)
        scheduler.evaluateDue()
        scheduler.evaluateDue()
        scheduler(schedules, screening, announcementInstant.plusSeconds(60)).evaluateDue()
        assertEquals(1, screening.commands.size)
    }

    @Test
    fun `정확한 발표 제목이 없으면 다른 일정을 처리하지 않는다`() {
        val schedules = Schedules(title = "1차 발표")
        val screening = Screening()
        scheduler(schedules, screening, announcementInstant).evaluateDue()
        assertEquals("1차 합격 발표", schedules.lastTitle)
        assertFalse(schedules.processed)
        assertTrue(screening.commands.isEmpty())
    }

    @Test
    fun `산출 실패는 트랜잭션이 취소할 수 있도록 호출자에게 전파한다`() {
        val screening = object : EvaluateFirstScreeningUseCase {
            override fun evaluateFirst(command: EvaluateScreeningCommand): ScreeningResult = error("저장 실패")
        }
        assertThrows(IllegalStateException::class.java) {
            scheduler(Schedules(), screening, announcementInstant).evaluateDue()
        }
    }

    private fun scheduler(schedules: ScheduleRepository, screening: EvaluateFirstScreeningUseCase, now: Instant) =
        FirstScreeningScheduler(schedules, screening, Clock.fixed(now, ZoneOffset.UTC))

    private inner class Schedules(private val title: String = "1차 합격 발표") : ScheduleRepository {
        var processed = false
        var lastTitle: String? = null
        var lastNow: LocalDateTime? = null
        override fun claimFirstScreening(title: String, now: LocalDateTime): Boolean {
            lastTitle = title
            lastNow = now
            if (title != this.title || now.isBefore(announcement) || processed) return false
            processed = true
            return true
        }
        override fun findByTitle(title: String): Schedule? = error("unexpected query")
        override fun findByStartAtBetween(startAt: LocalDateTime, endAt: LocalDateTime): List<Schedule> = error("unexpected query")
        override fun save(schedule: Schedule): Schedule = error("unexpected save")
    }

    private class Screening : EvaluateFirstScreeningUseCase {
        val commands = mutableListOf<EvaluateScreeningCommand>()
        override fun evaluateFirst(command: EvaluateScreeningCommand): ScreeningResult {
            commands += command
            return ScreeningResult(command.dryRun, 0, 0, 0, Instant.EPOCH)
        }
    }
}
