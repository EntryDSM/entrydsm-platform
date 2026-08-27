package hs.kr.entrydsm.observability.application.port.out

import hs.kr.entrydsm.observability.domain.enum.ServiceName
import hs.kr.entrydsm.observability.domain.model.Cursor
import java.time.Instant

interface ServerLogStorePort {
    fun list(
        from: Instant,
        to: Instant,
        service: ServiceName?,
        status: StatusFilter?,
        cursor: Cursor?,
        size: Int,
    ): ServerLogPage
}

sealed interface StatusFilter {
    data class StatusClass(val leadingDigit: Char) : StatusFilter
    data class Exact(val code: Int) : StatusFilter
}

data class ServerLogEntry(
    val fingerprint: String,
    val service: ServiceName,
    val method: String,
    val path: String,
    val status: Int,
    val code: String,
    val grpcStatus: String?,
    val message: String,
    val count: Long,
    val firstOccurredAt: Instant,
    val lastOccurredAt: Instant,
)

data class ServerLogPage(
    val totalCount: Long,
    val items: List<ServerLogEntry>,
    val nextCursor: Cursor?,
    val hasNext: Boolean,
)
