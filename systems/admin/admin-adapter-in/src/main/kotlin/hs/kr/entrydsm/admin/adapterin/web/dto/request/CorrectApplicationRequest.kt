package hs.kr.entrydsm.admin.adapterin.web.dto.request

import hs.kr.entrydsm.admin.domain.command.ApplicationFormChanges
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class CorrectApplicationRequest(
    @field:NotBlank @field:Size(max = 500) val reason: String,
    val changes: ApplicationFormChanges,
)
