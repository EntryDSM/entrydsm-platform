package hs.kr.entrydsm.notification.adapterout.entity

import hs.kr.entrydsm.notification.domain.model.RecruitmentGuideline
import hs.kr.entrydsm.notification.domain.model.RecruitmentSchedule
import jakarta.persistence.Column
import jakarta.persistence.Embedded
import jakarta.persistence.Embeddable
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDate
import java.time.LocalDateTime

@Entity
@Table(name = "recruitment_guidelines")
open class RecruitmentGuidelineJpaEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null,

    @Column(name = "title", nullable = false, length = 255)
    var title: String = "",

    @Column(name = "description", nullable = false, columnDefinition = "TEXT")
    var description: String = "",

    @Embedded
    var schedule: RecruitmentScheduleEmbeddable = RecruitmentScheduleEmbeddable(),

    @Column(name = "created_at", nullable = false)
    var createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now(),
) {
    fun toDomain(): RecruitmentGuideline =
        RecruitmentGuideline(
            id = requireNotNull(id),
            title = title,
            description = description,
            schedule = schedule.toDomain(),
            createdAt = createdAt,
            updatedAt = updatedAt,
        )
}

@Embeddable
class RecruitmentScheduleEmbeddable(
    @Column(name = "application_start", nullable = false)
    var applicationStart: LocalDate = LocalDate.MIN,

    @Column(name = "application_end", nullable = false)
    var applicationEnd: LocalDate = LocalDate.MIN,

    @Column(name = "result_at", nullable = false)
    var resultAt: LocalDate = LocalDate.MIN,
) {
    fun toDomain(): RecruitmentSchedule =
        RecruitmentSchedule(
            applicationStart = applicationStart,
            applicationEnd = applicationEnd,
            resultAt = resultAt,
        )
}

