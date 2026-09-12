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
) {
    /**
     * 지원자가 이 조건에 걸리는지 봅니다.
     *
     * 값이 비어 있는 원서는 해당 조건을 건 검색에서 빠집니다. "대전으로 좁혀 봤더니
     * 지역 미기재 원서가 섞여 나오는" 쪽이 더 헷갈리기 때문입니다.
     */
    fun matches(applicant: Applicant): Boolean =
        matchesKeyword(applicant) &&
            regions.accepts(applicant.region) &&
            admissionTypes.accepts(applicant.admissionType) &&
            graduationStatuses.accepts(applicant.graduationStatus) &&
            statuses.accepts(applicant.status) &&
            (isSubmitted == null || applicant.isSubmitted == isSubmitted)

    private fun <T> Set<T>.accepts(value: T?): Boolean =
        isEmpty() || (value != null && contains(value))

    private fun matchesKeyword(applicant: Applicant): Boolean {
        val needle = keyword?.trim()?.lowercase()
        if (needle.isNullOrEmpty()) return true

        return applicant.name.lowercase().contains(needle) ||
            applicant.examineeNumber?.lowercase()?.contains(needle) == true
    }
}
