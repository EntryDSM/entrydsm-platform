package hs.kr.entrydsm.application.adapterin.web.dto.request

import hs.kr.entrydsm.application.domain.enum.Gender

data class UpdateFamilyRequest(
    val guardianName: String,
    val guardianPhoneNumber: String,
    val guardianGender: Gender,
    val guardianRelation: String,
    val address: AddressRequest,
)
