package hs.kr.entrydsm.application.application.port.`in`.command

import hs.kr.entrydsm.application.domain.enum.AdmissionType
import hs.kr.entrydsm.application.domain.enum.GraduationType
import hs.kr.entrydsm.application.domain.enum.Region
import java.time.YearMonth

data class UpdateTypeCommand(
    val applicantId: Long,
    val authorization: String? = null,
    val userId: Long? = null,
    val admissionType: AdmissionType,
    val region: Region,
    val graduationType: GraduationType,
    val graduationDate: YearMonth?,
)
