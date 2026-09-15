package hs.kr.entrydsm.configuration.domain.document

/** 수험표에 찍는 지원자 정보. application 이 가진 원서 값이라 작성 중이면 비어 있을 수 있다. */
data class Applicant(
    val name: String?,
    val schoolName: String?,
    val region: Region?,
    val admissionType: AdmissionType?,
    val photoFileId: Long?,
) {
    /** @property label 수험표에 찍는 한글 표기 */
    enum class Region(val label: String) {
        DAEJEON("대전"),
        NATIONAL("전국"),
    }

    /** @property label 수험표에 찍는 한글 표기 */
    enum class AdmissionType(val label: String) {
        REGULAR("일반전형"),
        MEISTER("마이스터전형"),
        SOCIAL("사회통합전형"),
    }
}
