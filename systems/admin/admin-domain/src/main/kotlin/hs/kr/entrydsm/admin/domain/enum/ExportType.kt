package hs.kr.entrydsm.admin.domain.enum

/**
 * 내보내기로 만들 수 있는 산출물 종류입니다.
 */
enum class ExportType {
    /** 1차 합격자 수험표를 수험 번호 순으로 이어 붙인 PDF 하나 */
    ADMISSION_TICKET,
    APPLICANT_LIST,
    FIRST_PASS_LIST,
    ADMISSION_FILE,
}
