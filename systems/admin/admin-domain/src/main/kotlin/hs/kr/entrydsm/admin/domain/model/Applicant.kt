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
 * @property receiptNumber 접수 순서대로 부여되는 접수 번호
 * @property examineeNumber 수험 번호. 일괄 발급 전에는 null
 * @property isSubmitted 원서 원본(우편) 도착 여부
 */
data class Applicant(
    val id: Long? = null,
    val receiptNumber: Int,
    val name: String,
    val birthDate: LocalDate,
    val phoneNumber: String,
    val region: Region,
    val admissionType: AdmissionType,
    val graduationStatus: GraduationStatus,
    val schoolName: String,
    val examineeNumber: String? = null,
    val isSubmitted: Boolean = false,
    val status: ApplicantStatus = ApplicantStatus.PENDING,
    val score: ApplicantScore? = null,
    val submittedAt: Instant? = null,
    val updatedAt: Instant? = null,
)
