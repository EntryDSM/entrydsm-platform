package hs.kr.entrydsm.observability.application.port.`in`

import hs.kr.entrydsm.observability.application.port.out.ServerLogPage
import hs.kr.entrydsm.observability.domain.enum.ServiceName
import hs.kr.entrydsm.observability.domain.model.Cursor
import java.time.Instant

interface GetServerLogsUseCase {
    fun getLogs(
        service: ServiceName?,
        status: String?,
        from: Instant?,
        to: Instant?,
        size: Int,
        cursor: Cursor?,
    ): ServerLogPage
}
