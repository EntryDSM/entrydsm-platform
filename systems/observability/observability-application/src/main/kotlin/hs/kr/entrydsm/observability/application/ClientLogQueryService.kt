package hs.kr.entrydsm.observability.application

import hs.kr.entrydsm.observability.application.port.`in`.GetClientLogsUseCase
import hs.kr.entrydsm.observability.application.port.out.ClientLogPage
import hs.kr.entrydsm.observability.application.port.out.ClientLogStorePort
import hs.kr.entrydsm.observability.domain.enum.ErrorCode
import hs.kr.entrydsm.observability.domain.enum.LogLevel
import hs.kr.entrydsm.observability.domain.exception.MonitorDomainException
import hs.kr.entrydsm.observability.domain.model.Cursor
import java.time.Clock
import java.time.Duration
import java.time.Instant

class ClientLogQueryService(
    private val clientLogStorePort: ClientLogStorePort,
    private val clock: Clock,
) : GetClientLogsUseCase {

    override fun getLogs(levels: Set<LogLevel>?, from: Instant?, to: Instant?, size: Int, cursor: Cursor?): ClientLogPage {
        val now = Instant.now(clock)
        val resolvedTo = to ?: now
        // to만 지정한 과거 조회에서 from이 to보다 뒤가 되지 않도록 기준을 resolvedTo로 잡는다.
        val resolvedFrom = from ?: resolvedTo.minus(DEFAULT_LOOKBACK)
        if (resolvedFrom.isAfter(resolvedTo) || Duration.between(resolvedFrom, resolvedTo) > MAX_RANGE) {
            throw MonitorDomainException(ErrorCode.INVALID_TIME_RANGE)
        }
        return clientLogStorePort.list(resolvedFrom, resolvedTo, levels, cursor, size.coerceIn(1, MAX_SIZE))
    }

    companion object {
        private val DEFAULT_LOOKBACK: Duration = Duration.ofHours(1)
        private val MAX_RANGE: Duration = Duration.ofDays(7)
        private const val MAX_SIZE = 100
    }
}
