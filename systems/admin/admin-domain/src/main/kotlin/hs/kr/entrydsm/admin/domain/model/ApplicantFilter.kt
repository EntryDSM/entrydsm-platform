package hs.kr.entrydsm.admin.domain.model

import hs.kr.entrydsm.admin.domain.enum.AdmissionType
import hs.kr.entrydsm.admin.domain.enum.ApplicantStatus
import hs.kr.entrydsm.admin.domain.enum.GraduationStatus
import hs.kr.entrydsm.admin.domain.enum.Region

/**
 * 지원자 목록 조회와 내보내기에서 함께 쓰는 필터 조건입니다.
 *
 * 비어 있는 컬렉션과 null은 모두 "해당 조건으로 거르지 않음"을 뜻합니다.
 *
 * @property keyword 이름 또는 수험 번호 부분 일치 검색어
 */
data class ApplicantFilter(
    val keyword: String? = null,
    val regions: Set<Region> = emptySet(),
    val admissionTypes: Set<AdmissionType> = emptySet(),
    val graduationStatuses: Set<GraduationStatus> = emptySet(),
    val isSubmitted: Boolean? = null,
    val statuses: Set<ApplicantStatus> = emptySet(),
)
