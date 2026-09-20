package hs.kr.entrydsm.admin.domain.model

import hs.kr.entrydsm.admin.domain.enum.AdmissionType
import hs.kr.entrydsm.admin.domain.enum.ApplicantStatus
import hs.kr.entrydsm.admin.domain.enum.GraduationStatus
import hs.kr.entrydsm.admin.domain.enum.Region
import java.time.Instant
import java.time.LocalDate

/**
 * 관리자가 조회하고 관리하는 지원자입니다.
 *
 * 원서 내용(인적사항·지역·전형·학력·중학교·총점·제출)은 application 이 갖고, admin 은 전형 진행
 * (수험 번호·원본 도착·전형 상태)만 갖습니다. 두 쪽을 합쳐 만듭니다.
 *
 * 원서 제출 검증이 요구하는 값이 몇 개뿐이라, 제출된 원서에도 application 쪽 값이 비어 있을 수
 * 있습니다. 목록·상세·엑셀은 빈 값을 그대로 찍습니다.
 *
 * @property id application 의 applicantId. 접수 순서대로 매겨지므로 접수 번호를 겸한다
 * @property submittedAt 원서를 제출한 시각 (application)
 * @property examineeNumber 수험 번호. 일괄 발급 전에는 null
 * @property isArrived 원서 원본(우편) 도착 여부
 * @property arrivedAt 원서 원본이 도착한 시각 (admin)
 */
data class Applicant(
    val id: Long,
    val name: String? = null,
    val birthDate: LocalDate? = null,
    val phoneNumber: String? = null,
    val region: Region? = null,
    val admissionType: AdmissionType? = null,
    val graduationStatus: GraduationStatus? = null,
    val schoolName: String? = null,
    val totalScore: Double? = null,
    val submittedAt: Instant? = null,
    val examineeNumber: String? = null,
    val isArrived: Boolean = false,
    val status: ApplicantStatus = ApplicantStatus.PENDING,
    val arrivedAt: Instant? = null,
    val updatedAt: Instant? = null,
) {
    /** 서류에 찍는 접수 번호. 접수 순서인 [id] 를 네 자리로 채운다. 9999 번을 넘으면 자릿수가 늘어난다. */
    val receiptNumber: String get() = id.toString().padStart(4, '0')
}
