package hs.kr.entrydsm.admin.domain.enum

/**
 * 내보내기로 만들 수 있는 산출물 종류입니다.
 */
enum class ExportType {
    /** 1차 합격자 수험표를 수험 번호 순으로 한 시트에 이어 그린 xlsx 하나 */
    ADMISSION_TICKET,
    FIRST_PASS,
    ADMISSION_FILE,
    APPLICATION_CHECKLIST,
    ESSAYS,
}
