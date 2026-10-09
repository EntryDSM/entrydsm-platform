package hs.kr.entrydsm.application.administration.adapterout.entity

import hs.kr.entrydsm.application.administration.domain.schedule.Schedule
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes

@Entity
@Table(name = "schedule")
class ScheduleJpaEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(nullable = false, unique = true, length = 100)
    val title: String,

    // 일정은 서울 현지 시각이므로 JDBC Timestamp의 시간대 변환을 거치지 않는다.
    @JdbcTypeCode(SqlTypes.LOCAL_DATE_TIME)
    @Column(name = "start_at", nullable = false)
    val startAt: LocalDateTime,

    @JdbcTypeCode(SqlTypes.LOCAL_DATE_TIME)
    @Column(name = "end_at", nullable = false)
    val endAt: LocalDateTime,
    @JdbcTypeCode(SqlTypes.LOCAL_DATE_TIME)
    // 일정 수정이 자동 산출 직전에 읽은 미처리 값을 다시 쓰지 못하게 한다.
    @Column(name = "first_screening_processed_at", updatable = false)
    val firstScreeningProcessedAt: LocalDateTime? = null,
) {
    fun toDomain() = Schedule(id, title, startAt, endAt, firstScreeningProcessedAt)

    companion object {
        fun from(schedule: Schedule) = ScheduleJpaEntity(
            schedule.id,
            schedule.title,
            schedule.startAt,
            schedule.endAt,
            schedule.firstScreeningProcessedAt,
        )
    }
}
