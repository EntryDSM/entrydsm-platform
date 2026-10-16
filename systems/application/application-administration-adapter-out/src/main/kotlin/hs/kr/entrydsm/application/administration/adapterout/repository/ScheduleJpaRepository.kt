package hs.kr.entrydsm.application.administration.adapterout.repository

import hs.kr.entrydsm.application.administration.adapterout.entity.ScheduleJpaEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDateTime
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface ScheduleJpaRepository : JpaRepository<ScheduleJpaEntity, Long> {
    fun findAllByStartAtGreaterThanEqualAndStartAtLessThanOrderByStartAt(
        startAt: LocalDateTime,
        endAt: LocalDateTime,
    ): List<ScheduleJpaEntity>

    fun findByTitle(title: String): ScheduleJpaEntity?

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM ScheduleJpaEntity s WHERE s.title = :title")
    fun findByTitleForUpdate(@Param("title") title: String): ScheduleJpaEntity?

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE ScheduleJpaEntity s SET s.firstScreeningProcessedAt = :now WHERE s.title = :title AND s.startAt <= :now AND s.firstScreeningProcessedAt IS NULL")
    fun claimFirstScreening(@Param("title") title: String, @Param("now") now: LocalDateTime): Int

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE ScheduleJpaEntity s SET s.finalScreeningProcessedAt = :now WHERE s.title = :title AND s.startAt <= :now AND s.finalScreeningProcessedAt IS NULL")
    fun claimFinalScreening(@Param("title") title: String, @Param("now") now: LocalDateTime): Int

}
