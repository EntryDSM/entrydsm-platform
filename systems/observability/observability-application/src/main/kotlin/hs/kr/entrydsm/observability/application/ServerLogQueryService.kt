package hs.kr.entrydsm.observability.application

import hs.kr.entrydsm.observability.application.port.`in`.GetServerLogsUseCase
import hs.kr.entrydsm.observability.application.port.out.ServerLogPage
import hs.kr.entrydsm.observability.application.port.out.ServerLogStorePort
import hs.kr.entrydsm.observability.application.port.out.StatusFilter
import hs.kr.entrydsm.observability.domain.enum.ErrorCode
import hs.kr.entrydsm.observability.domain.enum.ServiceName
import hs.kr.entrydsm.observability.domain.exception.MonitorDomainException
import hs.kr.entrydsm.observability.domain.model.Cursor
import java.time.Clock
import java.time.Duration
import java.time.Instant
import org.springframework.stereotype.Service

@Service
class ServerLogQueryService(
    private val serverLogStorePort: ServerLogStorePort,
    private val clock: Clock,
) : GetServerLogsUseCase {

    override fun getLogs(
        service: ServiceName?,
        status: String?,
        from: Instant?,
        to: Instant?,
        size: Int,
        cursor: Cursor?,
    ): ServerLogPage {
        val now = Instant.now(clock)
        val resolvedTo = to ?: now
        val resolvedFrom = from ?: now.minus(DEFAULT_LOOKBACK)
        if (resolvedFrom.isAfter(resolvedTo) || Duration.between(resolvedFrom, resolvedTo) > MAX_RANGE) {
            throw MonitorDomainException(ErrorCode.INVALID_TIME_RANGE)
        }
        val statusFilter = parseStatus(status)
        return serverLogStorePort.list(resolvedFrom, resolvedTo, service, statusFilter, cursor, size.coerceIn(1, MAX_SIZE))
    }

    private fun parseStatus(status: String?): StatusFilter? {
        if (status == null) return null
        val trimmed = status.trim()
        if (trimmed.length == 3 && trimmed[1] == 'x' && trimmed[2] == 'x' && trimmed[0].isDigit()) {
            return StatusFilter.StatusClass(trimmed[0])
        }
        return trimmed.toIntOrNull()?.let { StatusFilter.Exact(it) }
            ?: throw MonitorDomainException(ErrorCode.INVALID_PAYLOAD)
    }

    companion object {
        private val DEFAULT_LOOKBACK: Duration = Duration.ofHours(1)
        private val MAX_RANGE: Duration = Duration.ofDays(7)
        private const val MAX_SIZE = 100
    }
}
