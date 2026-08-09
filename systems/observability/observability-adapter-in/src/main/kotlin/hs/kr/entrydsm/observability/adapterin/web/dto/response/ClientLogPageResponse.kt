package hs.kr.entrydsm.observability.adapterin.web.dto.response

import hs.kr.entrydsm.observability.domain.enum.LogLevel
import hs.kr.entrydsm.observability.domain.enum.LogSource
import java.time.Instant

data class ClientLogPageResponse(
    val totalCount: Long,
    val errorCount: Long,
    val warnCount: Long,
    val items: List<ClientLogItemResponse>,
    val nextCursor: String?,
    val hasNext: Boolean,
)

data class ClientLogItemResponse(
    val fingerprint: String,
    val level: LogLevel,
    val message: String,
    val source: LogSource,
    val pageUrl: String,
    val browser: String?,
    val os: String?,
    val count: Long,
    val firstOccurredAt: Instant,
    val lastOccurredAt: Instant,
)
