package hs.kr.entrydsm.identity.application.port.`in`.result

import hs.kr.entrydsm.identity.domain.enum.ApplicantStatus
import hs.kr.entrydsm.identity.domain.enum.SignupType
import java.time.LocalDate

data class ProfileResult(
    val name: String,
    val phone: String,
    val birthdate: LocalDate,
    val signupType: SignupType,
    val applicantStatus: ApplicantStatus,
)
