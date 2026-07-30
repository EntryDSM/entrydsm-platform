package hs.kr.entrydsm.application.adapterin.web.dto.request

import hs.kr.entrydsm.application.domain.enum.AdmissionType
import hs.kr.entrydsm.application.domain.enum.GraduationType
import hs.kr.entrydsm.application.domain.enum.Region

data class UpdateTypeRequest(
    val admissionType: AdmissionType,
    val region: Region,
    val graduationType: GraduationType,
    val graduationDate: String?,
)
