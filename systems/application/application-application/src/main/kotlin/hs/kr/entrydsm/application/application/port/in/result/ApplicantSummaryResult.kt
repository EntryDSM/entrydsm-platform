package hs.kr.entrydsm.application.application.port.`in`.result

import hs.kr.entrydsm.application.domain.enum.AdmissionType
import hs.kr.entrydsm.application.domain.enum.ApplicantStatus
import hs.kr.entrydsm.application.domain.enum.GraduationType
import hs.kr.entrydsm.application.domain.enum.Region
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * admin 이 전형을 진행하는 데 필요한 만큼만 추린 원서 정보입니다.
 *
 * @property score 성적이 아직 산출되지 않았으면 null
 */
data class ApplicantSummaryResult(
    val applicantId: Long,
    val userId: Long,
    val name: String,
    val birthdate: LocalDate?,
    val phoneNumber: String,
    val region: Region?,
    val admissionType: AdmissionType?,
    val graduationType: GraduationType?,
    val schoolName: String,
    val applicantStatus: ApplicantStatus,
    val submittedAt: LocalDateTime?,
    val score: ApplicantScoreResult?,
    val updatedAt: LocalDateTime,
)

data class ApplicantScoreResult(
    val subjectScore: Double,
    val attendanceScore: Double,
    val volunteerScore: Double,
    val totalScore: Double,
)
