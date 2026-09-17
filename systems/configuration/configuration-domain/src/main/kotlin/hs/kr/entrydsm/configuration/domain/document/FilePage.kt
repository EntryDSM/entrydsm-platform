package hs.kr.entrydsm.configuration.domain.document

data class FilePage(
    val items: List<DownloadableFile>,
    val totalElements: Long,
)
