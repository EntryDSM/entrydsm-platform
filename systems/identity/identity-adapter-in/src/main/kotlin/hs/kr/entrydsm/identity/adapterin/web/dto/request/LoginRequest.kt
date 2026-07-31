package hs.kr.entrydsm.identity.adapterin.web.dto.request

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class LoginRequest @JsonCreator(mode = JsonCreator.Mode.PROPERTIES) constructor(
    @field:NotBlank
    @JsonProperty("loginId") val loginId: String,
    @field:NotBlank
    @field:Size(max = 72)
    @JsonProperty("password") val password: String,
)
