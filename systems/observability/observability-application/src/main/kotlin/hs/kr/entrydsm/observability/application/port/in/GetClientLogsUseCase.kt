package hs.kr.entrydsm.observability.application.port.`in`

import hs.kr.entrydsm.observability.application.port.out.ClientLogPage
import hs.kr.entrydsm.observability.domain.enum.LogLevel
import hs.kr.entrydsm.observability.domain.model.Cursor
import java.time.Instant

interface GetClientLogsUseCase {
    /** @param levels null이면 전체 */
    fun getLogs(levels: Set<LogLevel>?, from: Instant?, to: Instant?, size: Int, cursor: Cursor?): ClientLogPage
}
