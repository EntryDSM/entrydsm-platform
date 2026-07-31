package hs.kr.entrydsm.identity.adapterin.web.dto.request

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import hs.kr.entrydsm.identity.domain.enum.SignupType
import java.time.LocalDate
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

data class SignupRequest @JsonCreator(mode = JsonCreator.Mode.PROPERTIES) constructor(
    @field:NotBlank
    @field:Size(min = 8, max = 72)
    @JsonProperty("password") val password: String,
    @field:NotBlank
    @field:Size(max = 50)
    @JsonProperty("name") val name: String,
    @field:NotBlank
    @field:Pattern(regexp = "^01\\d{8,9}$")
    @JsonProperty("phone") val phone: String,
    @field:NotNull
    @JsonProperty("birthdate") val birthdate: LocalDate,
    @field:NotNull
    @JsonProperty("signupType") val signupType: SignupType,
)
