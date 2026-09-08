package hs.kr.entrydsm.configuration.adapterout

import hs.kr.entrydsm.configuration.adapterout.entity.ScheduleJpaEntity
import hs.kr.entrydsm.configuration.adapterout.repository.ScheduleJpaRepository
import hs.kr.entrydsm.configuration.domain.schedule.Schedule
import hs.kr.entrydsm.configuration.domain.schedule.port.out.ScheduleRepository
import org.springframework.stereotype.Component
import java.time.LocalDateTime

@Component
class SchedulePersistenceAdapter(
    private val repository: ScheduleJpaRepository,
) : ScheduleRepository {
    override fun findByStartAtBetween(startAt: LocalDateTime, endAt: LocalDateTime): List<Schedule> =
        repository.findAllByStartAtGreaterThanEqualAndStartAtLessThanOrderByStartAt(startAt, endAt)
            .map(ScheduleJpaEntity::toDomain)

    override fun findByTitle(title: String): Schedule? = repository.findByTitle(title)?.toDomain()

    override fun save(schedule: Schedule): Schedule = repository.save(ScheduleJpaEntity.from(schedule)).toDomain()
}
