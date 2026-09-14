package hs.kr.entrydsm.admin.adapterin.web.dto.request

import com.fasterxml.jackson.annotation.JsonProperty
import hs.kr.entrydsm.admin.domain.enum.AdmissionType
import hs.kr.entrydsm.admin.domain.enum.ApplicantStatus
import hs.kr.entrydsm.admin.domain.enum.ExportType
import hs.kr.entrydsm.admin.domain.enum.GraduationStatus
import hs.kr.entrydsm.admin.domain.enum.Region
import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size

data class UpdateScorePolicyRequest(
    @field:NotNull
    @field:Valid
    val weights: ScoreWeightsRequest?,
    @field:NotNull
    @field:Min(0)
    @field:Max(6)
    val roundingScale: Int?,
    val recalculate: Boolean = false,
)

data class ScoreWeightsRequest(
    @field:NotNull
    val subject: Double?,
    @field:NotNull
    val attendance: Double?,
    @field:NotNull
    val volunteer: Double?,
)

/**
 * 지역 × 전형 정원 전체. 조합 누락·음수 검증은 도메인 모델이 한다.
 */
data class UpdateAdmissionQuotaRequest(
    @field:NotNull
    val quotas: Map<Region, Map<AdmissionType, Int>>?,
)

data class EvaluateScreeningRequest(
    val dryRun: Boolean = false,
)

data class CreateExportRequest(
    @field:NotNull
    val type: ExportType?,
    val filter: ExportFilterRequest? = null,
)

/**
 * 지원자 목록 조회(`GET /applicants`)와 같은 조건. 비어 있거나 null 이면 거르지 않는다.
 */
data class ExportFilterRequest(
    val keyword: String? = null,
    val regions: Set<Region>? = null,
    val admissionTypes: Set<AdmissionType>? = null,
    val graduationStatuses: Set<GraduationStatus>? = null,
    @param:JsonProperty("isSubmitted")
    @get:JsonProperty("isSubmitted")
    val isSubmitted: Boolean? = null,
    val statuses: Set<ApplicantStatus>? = null,
)

data class CreateNoticeRequest(
    @field:NotBlank
    @field:Size(max = 200)
    val title: String,
    @field:NotBlank
    val content: String,
    /**
     * 공지 분류. notification 공지 목록의 category 값(ADMISSION_NOTICE, PROSPECTIVE_STUDENT)과 같다.
     *
     * 한글 이름(입학 공지사항, 예비 신입생 안내)과 Notion 명세의 영문 이름
     * (Admissions Notice, Prospective Students Notice)도 같은 값으로 치환해 받는다.
     */
    @field:NotBlank
    val division: String = "ADMISSION_NOTICE",
    @param:JsonProperty("isPinned")
    @get:JsonProperty("isPinned")
    val isPinned: Boolean = false,
    val attachmentIds: List<String> = emptyList(),
)

/**
 * 보낸 필드만 바꾸고, 없거나 null 인 필드는 유지한다. 값은 [CreateNoticeRequest] 와 같다.
 *
 * 빈 문자열·잘못된 분류는 notification 이 거절한다(400).
 */
data class UpdateNoticeRequest(
    @field:Size(max = 200)
    val title: String? = null,
    val content: String? = null,
    val division: String? = null,
    @param:JsonProperty("isPinned")
    @get:JsonProperty("isPinned")
    val isPinned: Boolean? = null,
    /** 목록 전체로 교체한다. 빈 목록이면 첨부를 모두 뗀다. */
    val attachmentIds: List<String>? = null,
)

data class AnswerQuestionRequest(
    @field:NotBlank
    val content: String,
)
