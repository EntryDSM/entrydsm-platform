package hs.kr.entrydsm.application.adapterin.web.dto.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class AddressRequest(
    @field:NotBlank
    @field:Size(max = 10)
    val zipCode: String,
    @field:NotBlank
    @field:Size(max = 255)
    val addressBase: String,
    @field:NotBlank
    @field:Size(max = 255)
    val addressDetail: String,
)

