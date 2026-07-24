package hs.kr.entrydsm.identity.adapterin.web.dto.request

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import java.time.LocalDate

data class PasswordResetRequest @JsonCreator(mode = JsonCreator.Mode.PROPERTIES) constructor(
    @JsonProperty("loginId") val loginId: String,
    @JsonProperty("name") val name: String,
    @JsonProperty("birthdate") val birthdate: LocalDate,
    @JsonProperty("newPassword") val newPassword: String,
)
