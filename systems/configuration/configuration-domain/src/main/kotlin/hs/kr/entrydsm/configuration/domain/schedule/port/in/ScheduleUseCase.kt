package hs.kr.entrydsm.configuration.domain.schedule.port.`in`

import hs.kr.entrydsm.configuration.domain.schedule.Schedule
import java.time.LocalDateTime

interface ScheduleUseCase {
    fun findByYear(year: Int): List<Schedule>

    /** 제목은 일정마다 하나다. 없으면 null. */
    fun findByTitle(title: String): Schedule?

    fun create(title: String, startAt: LocalDateTime, endAt: LocalDateTime): Schedule

    fun updateAll(schedules: List<Schedule>): List<Schedule>
}
