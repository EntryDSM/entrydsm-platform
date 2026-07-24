package hs.kr.entrydsm.identity.application.port.`in`.command

import java.time.LocalDate

data class PasswordResetCommand(
    val loginId: String,
    val name: String,
    val birthdate: LocalDate,
    val newPassword: String,
)
