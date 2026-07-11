package hs.kr.entrydsm.identity.adapterin.web.dto.request

import java.time.LocalDate

data class PasswordResetRequest(
    val loginId: String,
    val name: String,
    val birthdate: LocalDate,
    val newPassword: String,
)
