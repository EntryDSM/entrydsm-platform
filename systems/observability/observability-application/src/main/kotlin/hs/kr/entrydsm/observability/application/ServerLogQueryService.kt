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
        // to만 지정한 과거 조회에서 from이 to보다 뒤가 되지 않도록 기준을 resolvedTo로 잡는다.
        val resolvedFrom = from ?: resolvedTo.minus(DEFAULT_LOOKBACK)
        if (resolvedFrom.isAfter(resolvedTo) || Duration.between(resolvedFrom, resolvedTo) > MAX_RANGE) {
            throw MonitorDomainException(ErrorCode.INVALID_TIME_RANGE)
        }
        val statusFilter = parseStatus(status)
        return serverLogStorePort.list(resolvedFrom, resolvedTo, service, statusFilter, cursor, size.coerceIn(1, MAX_SIZE))
    }

    /** 1xx~5xx와 100..599만 유효한 필터로 받는다. 0xx, 6xx, 범위 밖 정수는 거부한다. */
    private fun parseStatus(status: String?): StatusFilter? {
        if (status == null) return null
        val trimmed = status.trim()
        if (trimmed.length == 3 && trimmed.endsWith("xx")) {
            return trimmed[0].takeIf { it in STATUS_CLASSES }?.let { StatusFilter.StatusClass(it) }
                ?: throw MonitorDomainException(ErrorCode.INVALID_PAYLOAD)
        }
        return trimmed.toIntOrNull()?.takeIf { it in STATUS_CODE_RANGE }?.let { StatusFilter.Exact(it) }
            ?: throw MonitorDomainException(ErrorCode.INVALID_PAYLOAD)
    }

    companion object {
        private val DEFAULT_LOOKBACK: Duration = Duration.ofHours(1)
        private val MAX_RANGE: Duration = Duration.ofDays(7)
        private const val MAX_SIZE = 100
        private val STATUS_CLASSES = '1'..'5'
        private val STATUS_CODE_RANGE = 100..599
    }
}
