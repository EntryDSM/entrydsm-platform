package hs.kr.entrydsm.configuration.domain.document

/** 서명 URL 을 붙인 파일. document API 는 적재·조회 모두 이것을 돌려준다. */
data class DownloadableFile(
    val document: FileDocument,
    val downloadUrl: String,
    val expiresIn: Long,
)
