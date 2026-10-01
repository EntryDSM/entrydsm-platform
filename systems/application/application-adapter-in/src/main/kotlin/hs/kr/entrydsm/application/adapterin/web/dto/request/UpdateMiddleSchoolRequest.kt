package hs.kr.entrydsm.application.adapterin.web.dto.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

data class UpdateMiddleSchoolRequest(
    @field:NotBlank
    @field:Size(max = 20)
    val schoolCode: String,
    @field:NotBlank
    @field:Size(max = 50)
    val schoolName: String,
    @field:NotBlank
    @field:Pattern(regexp = "[0-9]{5}")
    val studentNumber: String,
    @field:NotBlank
    @field:Size(max = 16)
    val schoolPhone: String,
    @field:NotBlank
    @field:Size(max = 20)
    val teacherName: String,
)

