package hs.kr.entrydsm.admin.domain.enum

/**
 * 지원자가 선택한 전형 유형입니다.
 *
 * @property label 지원자 목록 엑셀 등 대외 문서에 출력하는 한글 표기
 */
enum class AdmissionType(val label: String) {
    GENERAL("일반전형"),
    MEISTER("마이스터전형"),
    SOCIAL("사회통합전형"),
}
