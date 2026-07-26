package hs.kr.entrydsm.identity.application.port.`in`.command

import hs.kr.entrydsm.identity.application.security.SensitiveValueMasker.REDACTED
import hs.kr.entrydsm.identity.domain.enum.SignupType
import java.time.LocalDate

data class SignupCommand(
    val password: String,
    val name: String,
    val phone: String,
    val birthdate: LocalDate,
    val signupType: SignupType,
) {
    override fun toString(): String =
        "SignupCommand(password=$REDACTED, name=$REDACTED, phone=$REDACTED, birthdate=$REDACTED, signupType=$signupType)"
}
