package hs.kr.entrydsm.observability.adapterin.web.dto.response

import java.time.Instant

data class ReportGeneratedResponse(
    val status: String,
    val downloadUrl: String,
    val fileName: String,
    val sizeBytes: Long,
    val expiresAt: Instant,
)
