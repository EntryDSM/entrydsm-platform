package hs.kr.entrydsm.identity.application.port.`in`.command

import hs.kr.entrydsm.identity.domain.SignupType
import java.time.LocalDate

data class SignupCommand(
    val password: String,
    val name: String,
    val phone: String,
    val birthdate: LocalDate,
    val signupType: SignupType,
)
