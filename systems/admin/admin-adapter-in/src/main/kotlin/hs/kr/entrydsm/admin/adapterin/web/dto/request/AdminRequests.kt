package hs.kr.entrydsm.admin.adapterin.web.dto.request

import com.fasterxml.jackson.annotation.JsonProperty
import hs.kr.entrydsm.admin.domain.enum.AdmissionType
import hs.kr.entrydsm.admin.domain.enum.ApplicantStatus
import hs.kr.entrydsm.admin.domain.enum.ExportType
import hs.kr.entrydsm.admin.domain.enum.NoticeCategory
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

data class ExportFilterRequest(
    val admissionTypes: Set<AdmissionType> = emptySet(),
    val statuses: Set<ApplicantStatus> = emptySet(),
)

/**
 * 공지사항 등록 요청입니다.
 *
 * `isPinned` 와 `attachmentIds` 는 공지를 소유한 notification 시스템이 아직 저장하지 않습니다.
 * 기존 클라이언트를 깨뜨리지 않으려고 받기만 하고 전달하지는 않습니다. 영속화가 필요해지면
 * notification 모델과 계약을 함께 넓혀야 합니다.
 */
data class CreateNoticeRequest(
    @field:NotBlank
    @field:Size(max = 200)
    val title: String,
    @field:NotBlank
    val content: String,
    @field:NotNull
    val category: NoticeCategory? = null,
    @param:JsonProperty("isPinned")
    @get:JsonProperty("isPinned")
    val isPinned: Boolean = false,
    val attachmentIds: List<String> = emptyList(),
)

data class AnswerQuestionRequest(
    @field:NotBlank
    val content: String,
)
