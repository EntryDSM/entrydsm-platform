package hs.kr.entrydsm.identity.adapterin.web.dto.request

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import hs.kr.entrydsm.identity.domain.enum.SignupType
import java.time.LocalDate

data class SignupRequest @JsonCreator(mode = JsonCreator.Mode.PROPERTIES) constructor(
    @JsonProperty("password") val password: String,
    @JsonProperty("name") val name: String,
    @JsonProperty("phone") val phone: String,
    @JsonProperty("birthdate") val birthdate: LocalDate,
    @JsonProperty("signupType") val signupType: SignupType,
)
