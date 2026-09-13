package hs.kr.entrydsm.observability.adapterin.web.dto.request

import hs.kr.entrydsm.observability.domain.enum.ServiceName
import hs.kr.entrydsm.observability.domain.enum.SessionEventType
import jakarta.validation.constraints.NotNull

data class SessionEventRequest(
    @field:NotNull val event: SessionEventType,
    val sessionId: String?,
    @field:NotNull val service: ServiceName,
    val pageUrl: String?,
)
