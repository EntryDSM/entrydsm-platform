package hs.kr.entrydsm.admin.domain.enum

/**
 * 지원자의 학력 구분입니다.
 *
 * @property label 대외 문서에 출력하는 한글 표기
 */
enum class GraduationStatus(val label: String) {
    EXPECTED("졸업예정"),
    GRADUATED("졸업"),
    GED("검정고시"),
}
