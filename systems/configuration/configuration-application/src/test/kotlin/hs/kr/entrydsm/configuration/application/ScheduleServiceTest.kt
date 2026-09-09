package hs.kr.entrydsm.configuration.application

import hs.kr.entrydsm.configuration.domain.schedule.Schedule
import hs.kr.entrydsm.configuration.domain.schedule.port.out.ScheduleRepository
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime

class ScheduleServiceTest {
    private val repository = FakeScheduleRepository()
    private val service = ScheduleService(repository)

    @Test
    fun `조회 연도의 시작 이상 다음 연도 시작 미만 일정을 조회한다`() {
        service.findByYear(2026)

        assertEquals(LocalDateTime.of(2026, 1, 1, 0, 0), repository.rangeStart)
        assertEquals(LocalDateTime.of(2027, 1, 1, 0, 0), repository.rangeEnd)
    }

    @Test
    fun `제목에 해당하는 일정의 기간을 수정한다`() {
        val startAt = LocalDateTime.of(2026, 4, 5, 21, 5, 34)
        val endAt = LocalDateTime.of(2026, 5, 5, 21, 5, 34)

        val updated = service.update("원서 접수", startAt, endAt)

        assertEquals(1L, updated.id)
        assertEquals(startAt, updated.startAt)
        assertEquals(endAt, updated.endAt)
    }

    @Test
    fun `새 일정을 저장한다`() {
        val startAt = LocalDateTime.of(2026, 4, 5, 21, 5, 34)
        val endAt = LocalDateTime.of(2026, 5, 5, 21, 5, 34)

        service.create("원서 접수", startAt, endAt)

        assertEquals("원서 접수", repository.savedSchedule?.title)
        assertEquals(startAt, repository.savedSchedule?.startAt)
        assertEquals(endAt, repository.savedSchedule?.endAt)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `종료보다 늦은 시작 시각은 거부한다`() {
        service.update(
            "원서 접수",
            LocalDateTime.of(2026, 5, 6, 0, 0),
            LocalDateTime.of(2026, 5, 5, 0, 0),
        )
    }

    private class FakeScheduleRepository : ScheduleRepository {
        private var schedule = Schedule(
            1,
            "원서 접수",
            LocalDateTime.of(2025, 1, 1, 0, 0),
            LocalDateTime.of(2025, 1, 2, 0, 0),
        )
        var rangeStart: LocalDateTime? = null
        var rangeEnd: LocalDateTime? = null
        var savedSchedule: Schedule? = null

        override fun findByStartAtBetween(startAt: LocalDateTime, endAt: LocalDateTime): List<Schedule> {
            rangeStart = startAt
            rangeEnd = endAt
            return listOf(schedule)
        }

        override fun findByTitle(title: String): Schedule? = schedule.takeIf { it.title == title }

        override fun save(schedule: Schedule): Schedule = schedule.also {
            this.schedule = it
            savedSchedule = it
        }
    }
}
