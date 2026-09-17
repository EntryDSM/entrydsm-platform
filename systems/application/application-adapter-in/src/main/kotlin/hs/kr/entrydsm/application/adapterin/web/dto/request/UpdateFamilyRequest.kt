package hs.kr.entrydsm.application.adapterin.web.dto.request

import hs.kr.entrydsm.application.domain.enum.Gender
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

data class UpdateFamilyRequest(
    @field:NotBlank
    @field:Size(max = 20)
    val guardianName: String,
    @field:Pattern(regexp = "^010-\\d{4}-\\d{4}$")
    val guardianPhoneNumber: String,
    val guardianGender: Gender,
    @field:NotBlank
    val guardianRelation: String,
    @field:Valid
    val address: AddressRequest,
)
