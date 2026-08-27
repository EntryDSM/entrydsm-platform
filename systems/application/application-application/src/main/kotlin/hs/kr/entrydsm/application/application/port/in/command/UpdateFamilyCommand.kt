package hs.kr.entrydsm.application.application.port.`in`.command

import hs.kr.entrydsm.application.domain.enum.Gender
import hs.kr.entrydsm.application.domain.enum.GuardianRelation

data class UpdateFamilyCommand(
    val applicantId: Long,
    val userId: Long? = null,
    val guardianName: String,
    val guardianPhoneNumber: String,
    val guardianGender: Gender,
    val guardianRelation: GuardianRelation,
    val zipCode: String,
    val addressBase: String,
    val addressDetail: String,
)
