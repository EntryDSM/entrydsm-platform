package hs.kr.entrydsm.application.administration.adapterout

import hs.kr.entrydsm.application.administration.adapterout.entity.ScheduleJpaEntity
import hs.kr.entrydsm.application.administration.adapterout.repository.ScheduleJpaRepository
import hs.kr.entrydsm.application.administration.domain.schedule.Schedule
import hs.kr.entrydsm.application.administration.domain.schedule.port.out.ScheduleRepository
import org.springframework.stereotype.Component
import java.time.LocalDateTime
import org.springframework.transaction.annotation.Transactional

@Component
class SchedulePersistenceAdapter(
    private val repository: ScheduleJpaRepository,
) : ScheduleRepository {
    override fun findByStartAtBetween(startAt: LocalDateTime, endAt: LocalDateTime): List<Schedule> =
        repository.findAllByStartAtGreaterThanEqualAndStartAtLessThanOrderByStartAt(startAt, endAt)
            .map(ScheduleJpaEntity::toDomain)

    override fun findByTitle(title: String): Schedule? = repository.findByTitle(title)?.toDomain()

    override fun save(schedule: Schedule): Schedule = repository.save(ScheduleJpaEntity.from(schedule)).toDomain()

    @Transactional
    override fun claimFirstScreening(title: String, now: LocalDateTime): Boolean =
        repository.claimFirstScreening(title, now) == 1
}
