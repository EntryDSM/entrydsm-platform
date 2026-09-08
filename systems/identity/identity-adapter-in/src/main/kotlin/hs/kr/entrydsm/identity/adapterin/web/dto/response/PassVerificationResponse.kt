package hs.kr.entrydsm.identity.adapterin.web.dto.response

import java.time.LocalDate

data class PassVerificationResponse(
    val phoneNumber: String,
    val name: String,
    val birthdate: LocalDate,
)
