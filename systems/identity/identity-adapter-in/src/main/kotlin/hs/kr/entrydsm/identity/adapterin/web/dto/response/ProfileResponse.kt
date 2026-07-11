package hs.kr.entrydsm.identity.adapterin.web.dto.response

import hs.kr.entrydsm.identity.domain.ApplicantStatus
import hs.kr.entrydsm.identity.domain.SignupType
import java.time.LocalDate

data class ProfileResponse(
    val name: String,
    val phone: String,
    val birthdate: LocalDate,
    val signupType: SignupType,
    val applicantStatus: ApplicantStatus,
)
