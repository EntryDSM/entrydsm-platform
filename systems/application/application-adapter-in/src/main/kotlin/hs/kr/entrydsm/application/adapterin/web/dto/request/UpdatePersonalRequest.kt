package hs.kr.entrydsm.application.adapterin.web.dto.request

import hs.kr.entrydsm.application.domain.enum.Gender
import hs.kr.entrydsm.application.domain.enum.SpecialAdmissionType

data class UpdatePersonalRequest(
    val photoFileId: Long,
    val name: String,
    val phoneNumber: String,
    val gender: Gender,
    val birthdate: String,
    val specialAdmissionType: SpecialAdmissionType? = null,
)
