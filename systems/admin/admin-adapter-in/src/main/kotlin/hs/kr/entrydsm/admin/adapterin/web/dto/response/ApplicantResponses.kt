package hs.kr.entrydsm.admin.adapterin.web.dto.response

import com.fasterxml.jackson.annotation.JsonProperty
import hs.kr.entrydsm.admin.domain.enum.AdmissionType
import hs.kr.entrydsm.admin.domain.enum.ApplicantStatus
import hs.kr.entrydsm.admin.domain.enum.GraduationStatus
import hs.kr.entrydsm.admin.domain.enum.Region
import java.time.Instant
import java.time.LocalDate

/**
 * `is` 접두사가 붙은 불리언은 Jackson이 기본적으로 접두사를 떼고 직렬화하므로
 * 명세의 필드명을 유지하려면 이름을 명시해야 합니다.
 */
data class ApplicantSummaryResponse(
    val applicantId: Long,
    val name: String?,
    val region: Region?,
    val admissionType: AdmissionType?,
    val graduationStatus: GraduationStatus?,
    val examineeNumber: String?,
    @get:JsonProperty("isArrived")
    val isArrived: Boolean,
    val status: ApplicantStatus,
)

/**
 * @property submittedAt 원서를 제출한 시각
 * @property arrivedAt 원서 원본(우편)이 도착한 시각
 */
data class ApplicantDetailResponse(
    val applicantId: Long,
    val name: String?,
    val birthDate: LocalDate?,
    val phoneNumber: String?,
    val region: Region?,
    val admissionType: AdmissionType?,
    val graduationStatus: GraduationStatus?,
    val schoolName: String?,
    val examineeNumber: String?,
    @get:JsonProperty("isArrived")
    val isArrived: Boolean,
    val status: ApplicantStatus,
    val score: ScoreResponse?,
    val submittedAt: Instant?,
    val arrivedAt: Instant?,
    val updatedAt: Instant?,
)

data class ScoreResponse(
    val totalScore: Double,
)

/**
 * 공통 규약의 목록 응답 형식입니다.
 */
data class PageResponse<T>(
    val items: List<T>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
)

data class ExamineeNumberIssueResponse(
    val issuedCount: Int,
    val skippedCount: Int,
    val totalTargets: Int,
)
