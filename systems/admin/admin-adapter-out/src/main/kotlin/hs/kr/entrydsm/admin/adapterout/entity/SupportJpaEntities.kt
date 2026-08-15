package hs.kr.entrydsm.admin.adapterout.entity

import hs.kr.entrydsm.admin.domain.enum.ExportStatus
import hs.kr.entrydsm.admin.domain.enum.ExportType
import hs.kr.entrydsm.admin.domain.model.ExportJob
import hs.kr.entrydsm.admin.domain.model.Notice
import hs.kr.entrydsm.admin.domain.model.QuestionAnswer
import hs.kr.entrydsm.admin.domain.model.ScorePolicy
import hs.kr.entrydsm.admin.domain.model.ScoreWeights
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

private const val ATTACHMENT_ID_DELIMITER = ","

@Entity
@Table(name = "score_policy")
class ScorePolicyJpaEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    val id: Long? = null,

    @Column(name = "policy_version", nullable = false)
    val policyVersion: Int,

    @Column(name = "subject_weight", nullable = false)
    val subjectWeight: Double,

    @Column(name = "attendance_weight", nullable = false)
    val attendanceWeight: Double,

    @Column(name = "volunteer_weight", nullable = false)
    val volunteerWeight: Double,

    @Column(name = "rounding_scale", nullable = false)
    val roundingScale: Int,

    @Column(name = "effective_from", nullable = false)
    val effectiveFrom: Instant,

    @Column(name = "updated_by", nullable = false, length = 50)
    val updatedBy: String,
) {
    fun toDomain(): ScorePolicy = ScorePolicy(
        id = id,
        policyVersion = policyVersion,
        weights = ScoreWeights(subjectWeight, attendanceWeight, volunteerWeight),
        roundingScale = roundingScale,
        effectiveFrom = effectiveFrom,
        updatedBy = updatedBy,
    )

    companion object {
        fun from(policy: ScorePolicy): ScorePolicyJpaEntity = ScorePolicyJpaEntity(
            id = policy.id,
            policyVersion = policy.policyVersion,
            subjectWeight = policy.weights.subject,
            attendanceWeight = policy.weights.attendance,
            volunteerWeight = policy.weights.volunteer,
            roundingScale = policy.roundingScale,
            effectiveFrom = policy.effectiveFrom,
            updatedBy = policy.updatedBy,
        )
    }
}

@Entity
@Table(name = "export_job")
class ExportJobJpaEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    val id: Long? = null,

    @Column(name = "export_job_id", nullable = false, unique = true, length = 40)
    val exportJobId: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 30)
    val type: ExportType,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    val status: ExportStatus,

    @Column(name = "object_key", length = 255)
    val objectKey: String? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant,

    @Column(name = "completed_at")
    val completedAt: Instant? = null,
) {
    fun toDomain(): ExportJob = ExportJob(
        id = id,
        exportJobId = exportJobId,
        type = type,
        status = status,
        objectKey = objectKey,
        createdAt = createdAt,
        completedAt = completedAt,
    )

    companion object {
        fun from(job: ExportJob): ExportJobJpaEntity = ExportJobJpaEntity(
            id = job.id,
            exportJobId = job.exportJobId,
            type = job.type,
            status = job.status,
            objectKey = job.objectKey,
            createdAt = job.createdAt,
            completedAt = job.completedAt,
        )
    }
}

@Entity
@Table(name = "notice")
class NoticeJpaEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    val id: Long? = null,

    @Column(name = "title", nullable = false, length = 200)
    val title: String,

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    val content: String,

    @Column(name = "is_pinned", nullable = false)
    val isPinned: Boolean = false,

    @Column(name = "attachment_ids", length = 500)
    val attachmentIds: String? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant,
) {
    fun toDomain(): Notice = Notice(
        id = id,
        title = title,
        content = content,
        isPinned = isPinned,
        attachmentIds = attachmentIds?.takeIf { it.isNotBlank() }
            ?.split(ATTACHMENT_ID_DELIMITER)
            ?: emptyList(),
        createdAt = createdAt,
    )

    companion object {
        fun from(notice: Notice, createdAt: Instant): NoticeJpaEntity = NoticeJpaEntity(
            id = notice.id,
            title = notice.title,
            content = notice.content,
            isPinned = notice.isPinned,
            attachmentIds = notice.attachmentIds
                .takeIf { it.isNotEmpty() }
                ?.joinToString(ATTACHMENT_ID_DELIMITER),
            createdAt = notice.createdAt ?: createdAt,
        )
    }
}

@Entity
@Table(name = "question_answer")
class QuestionAnswerJpaEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    val id: Long? = null,

    @Column(name = "question_id", nullable = false)
    val questionId: Long,

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    val content: String,

    @Column(name = "answered_by", nullable = false, length = 50)
    val answeredBy: String,

    @Column(name = "answered_at", nullable = false)
    val answeredAt: Instant,
) {
    fun toDomain(): QuestionAnswer = QuestionAnswer(
        id = id,
        questionId = questionId,
        content = content,
        answeredBy = answeredBy,
        answeredAt = answeredAt,
    )

    companion object {
        fun from(answer: QuestionAnswer, answeredAt: Instant): QuestionAnswerJpaEntity =
            QuestionAnswerJpaEntity(
                id = answer.id,
                questionId = answer.questionId,
                content = answer.content,
                answeredBy = answer.answeredBy,
                answeredAt = answer.answeredAt ?: answeredAt,
            )
    }
}
