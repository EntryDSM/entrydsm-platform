package hs.kr.entrydsm.configuration.application

import hs.kr.entrydsm.configuration.domain.schedule.Schedule
import hs.kr.entrydsm.configuration.domain.schedule.ScheduleNotFoundException
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
    fun `여러 제목의 일정 기간을 한 번에 수정한다`() {
        val startAt = LocalDateTime.of(2026, 4, 5, 21, 5, 34)
        val endAt = LocalDateTime.of(2026, 5, 5, 21, 5, 34)

        val updated = service.updateAll(
            listOf(
                Schedule(title = "원서 접수", startAt = startAt, endAt = endAt),
                Schedule(title = "1차 발표", startAt = startAt, endAt = endAt),
            ),
        )

        assertEquals(listOf(1L, 2L), updated.map(Schedule::id))
        assertEquals(listOf(startAt, startAt), updated.map(Schedule::startAt))
        assertEquals(listOf(endAt, endAt), updated.map(Schedule::endAt))
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

    @Test(expected = ScheduleNotFoundException::class)
    fun `없는 제목이 하나라도 있으면 수정을 중단한다`() {
        val startAt = LocalDateTime.of(2026, 4, 5, 21, 5, 34)
        val endAt = LocalDateTime.of(2026, 5, 5, 21, 5, 34)

        service.updateAll(
            listOf(
                Schedule(title = "원서 접수", startAt = startAt, endAt = endAt),
                Schedule(title = "없는 일정", startAt = startAt, endAt = endAt),
            ),
        )
    }

    private class FakeScheduleRepository : ScheduleRepository {
        private val schedules = listOf("원서 접수", "1차 발표").mapIndexed { index, title ->
            title to Schedule(
                index + 1L,
                title,
                LocalDateTime.of(2025, 1, 1, 0, 0),
                LocalDateTime.of(2025, 1, 2, 0, 0),
            )
        }.toMap().toMutableMap()
        var rangeStart: LocalDateTime? = null
        var rangeEnd: LocalDateTime? = null
        var savedSchedule: Schedule? = null

        override fun findByStartAtBetween(startAt: LocalDateTime, endAt: LocalDateTime): List<Schedule> {
            rangeStart = startAt
            rangeEnd = endAt
            return schedules.values.toList()
        }

        override fun findByTitle(title: String): Schedule? = schedules[title]

        override fun save(schedule: Schedule): Schedule = schedule.also {
            schedules[it.title] = it
            savedSchedule = it
        }
    }
}
