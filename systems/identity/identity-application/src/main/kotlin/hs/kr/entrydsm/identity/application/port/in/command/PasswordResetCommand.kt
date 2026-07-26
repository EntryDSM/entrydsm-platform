package hs.kr.entrydsm.identity.application.port.`in`.command

import hs.kr.entrydsm.identity.application.security.SensitiveValueMasker.REDACTED
import java.time.LocalDate

data class PasswordResetCommand(
    val loginId: String,
    val name: String,
    val birthdate: LocalDate,
    val newPassword: String,
) {
    override fun toString(): String =
        "PasswordResetCommand(loginId=$REDACTED, name=$REDACTED, birthdate=$REDACTED, newPassword=$REDACTED)"
}
