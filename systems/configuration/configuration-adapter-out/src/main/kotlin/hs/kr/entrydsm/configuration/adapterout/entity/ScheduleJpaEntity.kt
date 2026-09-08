package hs.kr.entrydsm.configuration.adapterout.entity

import hs.kr.entrydsm.configuration.domain.schedule.Schedule
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(name = "schedule")
class ScheduleJpaEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(nullable = false, unique = true, length = 100)
    val title: String,

    @Column(name = "start_at", nullable = false)
    val startAt: LocalDateTime,

    @Column(name = "end_at", nullable = false)
    val endAt: LocalDateTime,
) {
    fun toDomain() = Schedule(id, title, startAt, endAt)

    companion object {
        fun from(schedule: Schedule) = ScheduleJpaEntity(
            schedule.id,
            schedule.title,
            schedule.startAt,
            schedule.endAt,
        )
    }
}
