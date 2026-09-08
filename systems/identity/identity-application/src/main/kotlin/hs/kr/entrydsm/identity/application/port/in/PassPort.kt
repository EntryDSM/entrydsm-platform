package hs.kr.entrydsm.identity.application.port.`in`

import java.time.LocalDate

interface PassPort {
    fun generatePopup(redirectUrl: String): String

    fun verify(token: String): PassVerificationResult
}

data class PassVerificationResult(
    val phoneNumber: String,
    val name: String,
    val birthdate: LocalDate,
)
