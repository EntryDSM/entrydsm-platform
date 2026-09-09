package hs.kr.entrydsm.configuration.domain.schedule.port.`in`

import hs.kr.entrydsm.configuration.domain.schedule.Schedule
import java.time.LocalDateTime

interface ScheduleUseCase {
    fun findByYear(year: Int): List<Schedule>

    fun create(title: String, startAt: LocalDateTime, endAt: LocalDateTime): Schedule

    fun update(title: String, startAt: LocalDateTime, endAt: LocalDateTime): Schedule
}
