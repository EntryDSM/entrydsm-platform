package hs.kr.entrydsm.identity.application.port.`in`.result

import hs.kr.entrydsm.identity.domain.enum.AccountStatus
import hs.kr.entrydsm.identity.domain.enum.ApplicantStatus
import hs.kr.entrydsm.identity.domain.enum.SignupType
import java.time.Instant
import java.time.LocalDate

data class BasicInfoResult(
    val userId: Long,
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
