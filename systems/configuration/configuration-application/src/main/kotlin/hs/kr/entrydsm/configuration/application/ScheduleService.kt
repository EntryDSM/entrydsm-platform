package hs.kr.entrydsm.configuration.application

import hs.kr.entrydsm.configuration.domain.schedule.Schedule
import hs.kr.entrydsm.configuration.domain.schedule.ScheduleNotFoundException
import hs.kr.entrydsm.configuration.domain.schedule.port.`in`.ScheduleUseCase
import hs.kr.entrydsm.configuration.domain.schedule.port.out.ScheduleRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
@Transactional(readOnly = true)
class ScheduleService(
    private val scheduleRepository: ScheduleRepository,
) : ScheduleUseCase {

    override fun findByYear(year: Int): List<Schedule> =
        scheduleRepository.findByStartAtBetween(
            LocalDateTime.of(year, 1, 1, 0, 0),
            LocalDateTime.of(year + 1, 1, 1, 0, 0),
        )

    @Transactional
    override fun create(title: String, startAt: LocalDateTime, endAt: LocalDateTime): Schedule =
        scheduleRepository.save(Schedule(title = title, startAt = startAt, endAt = endAt))

    @Transactional
    override fun update(title: String, startAt: LocalDateTime, endAt: LocalDateTime): Schedule {
        val schedule = scheduleRepository.findByTitle(title) ?: throw ScheduleNotFoundException(title)
        return scheduleRepository.save(schedule.copy(startAt = startAt, endAt = endAt))
    }
}
