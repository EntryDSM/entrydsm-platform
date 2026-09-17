package hs.kr.entrydsm.application.application.port.`in`.result

import hs.kr.entrydsm.application.domain.enum.AdmissionType
import hs.kr.entrydsm.application.domain.enum.Region

/** 수험표처럼 원서 내용을 찍는 문서에 쓰는 지원자 정보. 작성 중인 원서면 비어 있을 수 있다. */
data class ApplicantResult(
    val applicantId: Long,
    val userId: Long,
    val name: String?,
    val schoolName: String?,
    val region: Region?,
    val admissionType: AdmissionType?,
    val photoFileId: String?,
)
