package hs.kr.entrydsm.admin.adapterout.entity

import hs.kr.entrydsm.admin.domain.enum.AdmissionType
import hs.kr.entrydsm.admin.domain.enum.ApplicantStatus
import hs.kr.entrydsm.admin.domain.enum.GraduationStatus
import hs.kr.entrydsm.admin.domain.enum.Region
import hs.kr.entrydsm.admin.domain.model.Applicant
import hs.kr.entrydsm.admin.domain.model.ApplicantScore
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.time.LocalDate

/**
 * 지원자 원서 테이블입니다.
 *
 * ponytail: 원서 접수의 원본 데이터는 application 시스템이 갖는 것이 맞다.
 * 그 시스템이 생기면 이 테이블은 조회 전용 투영으로 바꾸거나 gRPC 조회로 대체한다.
 */
@Entity
@Table(name = "applicant")
class ApplicantJpaEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    val id: Long? = null,

    @Column(name = "receipt_number", nullable = false, unique = true)
    val receiptNumber: Int,

    @Column(name = "name", nullable = false, length = 50)
    val name: String,

    @Column(name = "birth_date", nullable = false)
    val birthDate: LocalDate,

    @Column(name = "phone_number", nullable = false, length = 20)
    val phoneNumber: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "region", nullable = false, length = 20)
    val region: Region,

    @Enumerated(EnumType.STRING)
    @Column(name = "admission_type", nullable = false, length = 20)
    val admissionType: AdmissionType,

    @Enumerated(EnumType.STRING)
    @Column(name = "graduation_status", nullable = false, length = 20)
    val graduationStatus: GraduationStatus,

    @Column(name = "school_name", nullable = false, length = 100)
    val schoolName: String,

    @Column(name = "examinee_number", length = 20)
    val examineeNumber: String? = null,

    @Column(name = "is_submitted", nullable = false)
    val isSubmitted: Boolean = false,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    val status: ApplicantStatus = ApplicantStatus.PENDING,

    @Column(name = "subject_score")
    val subjectScore: Double? = null,

    @Column(name = "attendance_score")
    val attendanceScore: Double? = null,

    @Column(name = "volunteer_score")
    val volunteerScore: Double? = null,

    @Column(name = "total_score")
    val totalScore: Double? = null,

    @Column(name = "submitted_at")
    val submittedAt: Instant? = null,

    @Column(name = "updated_at")
    val updatedAt: Instant? = null,
) {
    fun toDomain(): Applicant = Applicant(
        id = id,
        receiptNumber = receiptNumber,
        name = name,
        birthDate = birthDate,
        phoneNumber = phoneNumber,
        region = region,
        admissionType = admissionType,
        graduationStatus = graduationStatus,
        schoolName = schoolName,
        examineeNumber = examineeNumber,
        isSubmitted = isSubmitted,
        status = status,
        score = totalScore?.let {
            ApplicantScore(
                subjectScore = subjectScore ?: 0.0,
                attendanceScore = attendanceScore ?: 0.0,
                volunteerScore = volunteerScore ?: 0.0,
                totalScore = it,
            )
        },
        submittedAt = submittedAt,
        updatedAt = updatedAt,
    )

    companion object {
        fun from(applicant: Applicant): ApplicantJpaEntity = ApplicantJpaEntity(
            id = applicant.id,
            receiptNumber = applicant.receiptNumber,
            name = applicant.name,
            birthDate = applicant.birthDate,
            phoneNumber = applicant.phoneNumber,
            region = applicant.region,
            admissionType = applicant.admissionType,
            graduationStatus = applicant.graduationStatus,
            schoolName = applicant.schoolName,
            examineeNumber = applicant.examineeNumber,
            isSubmitted = applicant.isSubmitted,
            status = applicant.status,
            subjectScore = applicant.score?.subjectScore,
            attendanceScore = applicant.score?.attendanceScore,
            volunteerScore = applicant.score?.volunteerScore,
            totalScore = applicant.score?.totalScore,
            submittedAt = applicant.submittedAt,
            updatedAt = applicant.updatedAt,
        )
    }
}
