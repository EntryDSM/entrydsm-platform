package hs.kr.entrydsm.admin.domain.enum

/**
 * 지원자가 선택한 모집 지역입니다.
 *
 * @property label 지원자 목록 엑셀 등 대외 문서에 출력하는 한글 표기
 */
enum class Region(val label: String) {
    DAEJEON("대전"),
    NATIONWIDE("전국"),
}
