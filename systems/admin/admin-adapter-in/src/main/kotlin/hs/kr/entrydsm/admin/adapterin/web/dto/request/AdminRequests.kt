package hs.kr.entrydsm.admin.adapterin.web.dto.request

import hs.kr.entrydsm.admin.domain.enum.AdmissionType
import hs.kr.entrydsm.admin.domain.enum.ApplicantStatus
import hs.kr.entrydsm.admin.domain.enum.ExportType
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

data class CreateNoticeRequest(
    @field:NotBlank
    @field:Size(max = 200)
    val title: String,
    @field:NotBlank
    val content: String,
    val isPinned: Boolean = false,
    val attachmentIds: List<String> = emptyList(),
)

data class AnswerQuestionRequest(
    @field:NotBlank
    val content: String,
)
