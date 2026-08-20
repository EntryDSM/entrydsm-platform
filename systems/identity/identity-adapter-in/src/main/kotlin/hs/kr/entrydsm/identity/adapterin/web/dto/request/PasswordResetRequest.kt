package hs.kr.entrydsm.identity.adapterin.web.dto.request

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import java.time.LocalDate
import hs.kr.entrydsm.identity.adapterin.web.validation.Utf8ByteLength
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

data class PasswordResetRequest @JsonCreator(mode = JsonCreator.Mode.PROPERTIES) constructor(
    @field:NotBlank
    @field:Pattern(regexp = "^01\\d{8,9}$")
    @JsonProperty("loginId") val loginId: String,
    @field:NotBlank
    @field:Size(max = 50)
    @JsonProperty("name") val name: String,
    @field:NotNull
    @JsonProperty("birthdate") val birthdate: LocalDate,
    @field:NotBlank
    @field:Size(min = 8)
    @field:Utf8ByteLength(max = 72)
    @JsonProperty("newPassword") val newPassword: String,
)
