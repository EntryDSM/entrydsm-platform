package hs.kr.entrydsm.application.application.port.`in`.result

import hs.kr.entrydsm.application.domain.enum.AdmissionType
import hs.kr.entrydsm.application.domain.enum.ApplicantStatus
import hs.kr.entrydsm.application.domain.enum.GraduationType
import hs.kr.entrydsm.application.domain.enum.Region
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 수험표를 찍는 문서와 admin 지원자 목록이 쓰는 지원자 정보. 작성 중인 원서면 비어 있을 수 있다.
 *
 * 제출 검증이 요구하는 값은 전형·이름·보호자·자기소개·학업계획뿐이라, 제출된 원서에도
 * 지역·학력·생년월일·연락처·중학교·총점이 비어 있을 수 있다.
 */
data class ApplicantResult(
    val applicantId: Long,
    val accountId: Long,
    val name: String?,
    val schoolName: String?,
    val region: Region?,
    val admissionType: AdmissionType?,
    val photoFileId: String?,
    val birthdate: LocalDate?,
    val phoneNumber: String?,
    val graduationType: GraduationType?,
    val totalScore: Double?,
    val status: ApplicantStatus,
    val submittedAt: LocalDateTime?,
)
