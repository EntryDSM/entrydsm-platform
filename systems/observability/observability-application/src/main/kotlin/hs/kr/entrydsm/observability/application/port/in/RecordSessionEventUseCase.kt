package hs.kr.entrydsm.observability.application.port.`in`

import hs.kr.entrydsm.observability.application.port.`in`.result.SessionEventResult
import hs.kr.entrydsm.observability.domain.enum.ServiceName
import hs.kr.entrydsm.observability.domain.enum.SessionEventType

interface RecordSessionEventUseCase {
    fun record(
        event: SessionEventType,
        sessionId: String?,
        service: ServiceName,
        userAgent: String?,
        clientIp: String,
    ): SessionEventResult
}
