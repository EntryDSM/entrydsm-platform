package hs.kr.entrydsm.identity.application.port.`in`.command

import java.time.LocalDate

data class PasswordResetCommand(
    val loginId: String,
    val name: String,
    val birthdate: LocalDate,
    val newPassword: String,
) {
    override fun toString(): String =
        "PasswordResetCommand(loginId=$loginId, name=$name, birthdate=$birthdate, newPassword=[REDACTED])"
}
