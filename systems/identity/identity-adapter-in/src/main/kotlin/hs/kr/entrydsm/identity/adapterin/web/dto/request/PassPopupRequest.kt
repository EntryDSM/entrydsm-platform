package hs.kr.entrydsm.identity.adapterin.web.dto.request

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class PassPopupRequest @JsonCreator(mode = JsonCreator.Mode.PROPERTIES) constructor(
    @field:NotBlank
    @field:Size(max = 2048)
    @JsonProperty("redirectUrl") val redirectUrl: String,
)
