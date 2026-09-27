package hs.kr.entrydsm.identity.adapterin.web.dto.response

import com.fasterxml.jackson.annotation.JsonProperty
import hs.kr.entrydsm.identity.domain.enum.AccountStatus
import hs.kr.entrydsm.identity.domain.enum.ApplicantStatus
import hs.kr.entrydsm.identity.domain.enum.SignupType
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
    @JsonProperty("is_sensitive_agree")
    val isSensitiveAgree: Boolean,
    val applicantStatus: ApplicantStatus,
    val createdAt: Instant,
    val updatedAt: Instant,
)
