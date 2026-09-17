package hs.kr.entrydsm.application.adapterin.web.dto.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class UpdateIntroductionRequest(
    @field:NotBlank
    @field:Size(max = 1600)
    val introduction: String,
)

