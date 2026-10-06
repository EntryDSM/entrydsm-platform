package hs.kr.entrydsm.application.administration.adapterout.repository

import hs.kr.entrydsm.application.administration.adapterout.entity.ScheduleJpaEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDateTime

interface ScheduleJpaRepository : JpaRepository<ScheduleJpaEntity, Long> {
    fun findAllByStartAtGreaterThanEqualAndStartAtLessThanOrderByStartAt(
        startAt: LocalDateTime,
        endAt: LocalDateTime,
    ): List<ScheduleJpaEntity>

    fun findByTitle(title: String): ScheduleJpaEntity?
}
