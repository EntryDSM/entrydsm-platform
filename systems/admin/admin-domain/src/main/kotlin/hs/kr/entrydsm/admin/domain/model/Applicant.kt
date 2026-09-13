package hs.kr.entrydsm.admin.domain.model

import hs.kr.entrydsm.admin.domain.enum.AdmissionType
import hs.kr.entrydsm.admin.domain.enum.ApplicantStatus
import hs.kr.entrydsm.admin.domain.enum.GraduationStatus
import hs.kr.entrydsm.admin.domain.enum.Region
import java.time.Instant
import java.time.LocalDate

/**
 * 관리자가 조회하고 관리하는 지원자(원서) 정보입니다.
 *
 * 원서 본문은 application 시스템이 소유하고 admin 은 조회만 합니다. 접수 번호부터
 * 아래쪽이 admin 이 직접 쓰는 전형 정보입니다.
 *
 * 원서 제출 시점에 채워지지 않을 수 있는 값은 null 로 옵니다. 전형은 이런 지원자를
 * 산출 대상에서 제외합니다([hs.kr.entrydsm.admin.domain.policy.ScreeningPolicy]).
 *
 * @property id application 시스템의 지원자 식별자
 * @property receiptNumber 접수 순서대로 부여되는 접수 번호
 * @property examineeNumber 수험 번호. 일괄 발급 전에는 null
 * @property isSubmitted 원서 원본(우편) 도착 여부
 */
data class Applicant(
    val id: Long,
    val name: String,
    val birthDate: LocalDate? = null,
    val phoneNumber: String = "",
    val region: Region? = null,
    val admissionType: AdmissionType? = null,
    val graduationStatus: GraduationStatus? = null,
    val schoolName: String = "",
    val submittedAt: Instant? = null,
    val receiptNumber: Int,
    val examineeNumber: String? = null,
    val isSubmitted: Boolean = false,
    val status: ApplicantStatus = ApplicantStatus.PENDING,
    val score: ApplicantScore? = null,
    val updatedAt: Instant? = null,
)
