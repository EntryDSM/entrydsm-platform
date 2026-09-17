package hs.kr.entrydsm.configuration.domain.document

/** 원서·수험표의 본인 판정과 수험표 내용에 쓰는 지원자 정보. application 이 가진 원서 값이라 작성 중이면 비어 있을 수 있다. */
data class Applicant(
    /** 원서 주인 계정. 본인 판정 기준이다. */
    val userId: Long,
    val name: String?,
    val schoolName: String?,
    val region: Region?,
    val admissionType: AdmissionType?,
    /** 원서에 적힌 증명사진 공개 ID. 학생이 보낸 값이라 그대로 믿지 않는다. */
    val photoFileId: String?,
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
