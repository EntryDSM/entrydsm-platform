package hs.kr.entrydsm.application.application.port.`in`.command

import hs.kr.entrydsm.application.domain.enum.Gender

data class UpdateFamilyCommand(
    val accountId: Long? = null,
    val guardianName: String,
    val guardianPhoneNumber: String,
    val guardianGender: Gender,
    val guardianRelation: String,
    val zipCode: String,
    val addressBase: String,
    val addressDetail: String,
)
