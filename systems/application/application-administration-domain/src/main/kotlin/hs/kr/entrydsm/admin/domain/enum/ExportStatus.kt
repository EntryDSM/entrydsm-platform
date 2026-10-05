package hs.kr.entrydsm.admin.domain.enum

/**
 * 내보내기 작업의 진행 상태입니다.
 */
enum class ExportStatus {
    PENDING,
    PROCESSING,
    COMPLETED,
    FAILED,
    ;

    fun isDownloadable(): Boolean = this == COMPLETED
}
