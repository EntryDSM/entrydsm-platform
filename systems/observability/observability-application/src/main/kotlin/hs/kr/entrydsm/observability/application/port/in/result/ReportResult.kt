package hs.kr.entrydsm.observability.application.port.`in`.result

import java.time.Instant

data class ReportResult(
    val downloadUrl: String,
    val fileName: String,
    val sizeBytes: Long,
    val expiresAt: Instant,
)
