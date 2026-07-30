package hs.kr.entrydsm.application.application.port.`in`.command

import hs.kr.entrydsm.application.domain.enum.Gender
import hs.kr.entrydsm.application.domain.enum.SpecialAdmissionType
import java.time.LocalDate

data class UpdatePersonalCommand(
    val applicantId: Long,
    val photoFileId: Long,
    val name: String,
    val phoneNumber: String,
    val gender: Gender,
    val birthdate: LocalDate,
    val specialAdmissionType: SpecialAdmissionType,
)
