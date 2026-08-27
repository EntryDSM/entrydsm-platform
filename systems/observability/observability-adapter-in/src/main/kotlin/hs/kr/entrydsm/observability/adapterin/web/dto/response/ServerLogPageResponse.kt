package hs.kr.entrydsm.observability.adapterin.web.dto.response

import hs.kr.entrydsm.observability.domain.enum.ServiceName
import java.time.Instant

data class ServerLogPageResponse(
    val totalCount: Long,
    val items: List<ServerLogItemResponse>,
    val nextCursor: String?,
    val hasNext: Boolean,
)

data class ServerLogItemResponse(
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
