package hs.kr.entrydsm.configuration.domain.schedule.port.out

import hs.kr.entrydsm.configuration.domain.schedule.Schedule
import java.time.LocalDateTime

interface ScheduleRepository {
    fun findByStartAtBetween(startAt: LocalDateTime, endAt: LocalDateTime): List<Schedule>

    fun findByTitle(title: String): Schedule?

    fun save(schedule: Schedule): Schedule
}
