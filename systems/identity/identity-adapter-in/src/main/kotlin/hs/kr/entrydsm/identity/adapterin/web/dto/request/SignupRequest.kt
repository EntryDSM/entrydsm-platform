package hs.kr.entrydsm.identity.adapterin.web.dto.request

import hs.kr.entrydsm.identity.domain.enum.SignupType
import java.time.LocalDate

data class SignupRequest(
    val password: String,
    val name: String,
    val phone: String,
    val birthdate: LocalDate,
    val signupType: SignupType,
)
