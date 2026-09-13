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
    override fun updateAll(schedules: List<Schedule>): List<Schedule> = schedules.map { requested ->
        // ponytail: 제목별 단건 조회라 요청 개수만큼 쿼리가 나간다. 연간 일정이 수십 건 수준이라 감수하고,
        // 늘어나면 ScheduleRepository에 findAllByTitleIn을 추가한다.
        val schedule = scheduleRepository.findByTitle(requested.title)
            ?: throw ScheduleNotFoundException(requested.title)
        scheduleRepository.save(schedule.copy(startAt = requested.startAt, endAt = requested.endAt))
    }
}
