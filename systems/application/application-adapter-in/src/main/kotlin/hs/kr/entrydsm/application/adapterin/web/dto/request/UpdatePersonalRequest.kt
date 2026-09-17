package hs.kr.entrydsm.application.adapterin.web.dto.request

import hs.kr.entrydsm.application.domain.enum.Gender
import hs.kr.entrydsm.application.domain.enum.SpecialAdmissionType
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

data class UpdatePersonalRequest(
    val photoFileId: String,
    @field:NotBlank
    @field:Size(max = 20)
    val name: String,
    @field:Pattern(regexp = "^010-\\d{4}-\\d{4}$")
    val phoneNumber: String,
    val gender: Gender,
    @field:NotBlank
    val birthdate: String,
    val specialAdmissionType: SpecialAdmissionType? = null,
)
