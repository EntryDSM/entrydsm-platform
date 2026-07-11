package hs.kr.entrydsm.identity.adapterin.web.dto.response

import hs.kr.entrydsm.identity.domain.AccountStatus
import hs.kr.entrydsm.identity.domain.ApplicantStatus
import hs.kr.entrydsm.identity.domain.SignupType
import java.time.Instant
import java.time.LocalDate

data class BasicInfoResponse(
    val userId: String,
    val role: String,
    val status: AccountStatus,
    val name: String,
    val phone: String,
    val birthdate: LocalDate,
    val signupType: SignupType,
    val applicantStatus: ApplicantStatus,
    val createdAt: Instant,
    val updatedAt: Instant,
)
