package hs.kr.entrydsm.admin.domain.model

/**
 * 내보내기 작업 조회 결과입니다.
 *
 * @property download 완료된 작업에만 채워지는 서명된 다운로드 링크
 */
data class ExportJobView(
    val job: ExportJob,
    val download: DownloadLink? = null,
)
