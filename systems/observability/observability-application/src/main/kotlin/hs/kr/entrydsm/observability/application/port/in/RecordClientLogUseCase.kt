package hs.kr.entrydsm.observability.application.port.`in`

import hs.kr.entrydsm.observability.application.port.`in`.result.ClientLogAcceptResult
import hs.kr.entrydsm.observability.domain.enum.LogLevel
import hs.kr.entrydsm.observability.domain.enum.LogSource
import java.time.Instant

interface RecordClientLogUseCase {
    fun record(sessionId: String, logs: List<ClientLogItem>, userAgent: String?, clientIp: String): ClientLogAcceptResult
}

data class ClientLogItem(
    val level: LogLevel,
    val source: LogSource,
    val message: String,
    val stack: String?,
    val pageUrl: String,
    val occurredAt: Instant,
)
