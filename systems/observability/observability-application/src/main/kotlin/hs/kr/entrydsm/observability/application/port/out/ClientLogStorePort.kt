package hs.kr.entrydsm.observability.application.port.out

import hs.kr.entrydsm.observability.domain.enum.LogLevel
import hs.kr.entrydsm.observability.domain.enum.LogSource
import hs.kr.entrydsm.observability.domain.model.Cursor
import java.time.Instant

interface ClientLogStorePort {
    fun record(input: ClientLogInput)

    /** 그룹(fingerprint) 단위 개수를 레벨별로 반환한다. */
    fun countByLevel(from: Instant, to: Instant): Map<LogLevel, Long>

    fun list(from: Instant, to: Instant, levels: Set<LogLevel>?, cursor: Cursor?, size: Int): ClientLogPage
}

data class ClientLogInput(
    val level: LogLevel,
    val source: LogSource,
    val message: String,
    val pageUrl: String,
    val browser: String?,
    val os: String?,
    val occurredAt: Instant,
)

data class ClientLogEntry(
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

data class ClientLogPage(
    val totalCount: Long,
    val errorCount: Long,
    val warnCount: Long,
    val items: List<ClientLogEntry>,
    val nextCursor: Cursor?,
    val hasNext: Boolean,
)
