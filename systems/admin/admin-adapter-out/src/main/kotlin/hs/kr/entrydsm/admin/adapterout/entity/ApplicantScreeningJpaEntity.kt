package hs.kr.entrydsm.admin.adapterout.entity

import hs.kr.entrydsm.admin.domain.enum.ApplicantStatus
import hs.kr.entrydsm.admin.domain.model.Applicant
import hs.kr.entrydsm.admin.domain.model.ApplicantScore
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/**
 * 지원자의 전형 정보입니다.
 *
 * 원서 본문(이름·지역·전형·학적)의 소유자는 application 시스템이고, 여기에는 admin 이
 * 직접 매기는 값만 둡니다. 식별자는 application 의 지원자 식별자를 그대로 씁니다.
 */
@Entity
@Table(name = "applicant_screening")
class ApplicantScreeningJpaEntity(
    @Id
    @Column(name = "applicant_id")
    val applicantId: Long = 0,

    @Column(name = "receipt_number", nullable = false, unique = true)
    var receiptNumber: Int = 0,

    @Column(name = "examinee_number", length = 20)
    var examineeNumber: String? = null,

    @Column(name = "document_received", nullable = false)
    var documentReceived: Boolean = false,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    var status: ApplicantStatus = ApplicantStatus.PENDING,

    @Column(name = "subject_score")
    var subjectScore: Double? = null,

    @Column(name = "attendance_score")
    var attendanceScore: Double? = null,

    @Column(name = "volunteer_score")
    var volunteerScore: Double? = null,

    @Column(name = "total_score")
    var totalScore: Double? = null,

    @Column(name = "updated_at")
    var updatedAt: Instant? = null,
) {
    fun applyFrom(applicant: Applicant) {
        receiptNumber = applicant.receiptNumber
        examineeNumber = applicant.examineeNumber
        documentReceived = applicant.isSubmitted
        status = applicant.status
        subjectScore = applicant.score?.subjectScore
        attendanceScore = applicant.score?.attendanceScore
        volunteerScore = applicant.score?.volunteerScore
        totalScore = applicant.score?.totalScore
        updatedAt = applicant.updatedAt
    }

    /** 전형 정보만 도메인 값으로 옮깁니다. 원서 본문은 호출자가 채웁니다. */
    fun score(): ApplicantScore? = totalScore?.let {
        ApplicantScore(
            subjectScore = subjectScore ?: 0.0,
            attendanceScore = attendanceScore ?: 0.0,
            volunteerScore = volunteerScore ?: 0.0,
            totalScore = it,
        )
    }

    companion object {
        fun from(applicant: Applicant): ApplicantScreeningJpaEntity =
            ApplicantScreeningJpaEntity(applicantId = applicant.id).apply { applyFrom(applicant) }
    }
}
