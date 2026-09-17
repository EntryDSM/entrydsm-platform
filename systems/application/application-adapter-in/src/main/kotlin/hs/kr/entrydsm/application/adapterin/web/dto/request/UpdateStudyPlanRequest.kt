package hs.kr.entrydsm.application.adapterin.web.dto.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class UpdateStudyPlanRequest(
    @field:NotBlank
    @field:Size(max = 1600)
    val studyPlan: String,
)

