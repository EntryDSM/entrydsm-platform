package hs.kr.entrydsm.configuration.domain.document

data class DownloadUrl(
    val fileName: String,
    val downloadUrl: String,
    val expiresIn: Long,
)
