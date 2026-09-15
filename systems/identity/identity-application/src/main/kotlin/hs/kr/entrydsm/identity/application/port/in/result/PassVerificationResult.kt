package hs.kr.entrydsm.identity.application.port.`in`.result

import java.time.LocalDate

data class PassVerificationResult(
    val phoneNumber: String,
    val name: String,
    val birthdate: LocalDate,
)