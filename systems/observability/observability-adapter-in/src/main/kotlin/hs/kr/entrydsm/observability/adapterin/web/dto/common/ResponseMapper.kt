package hs.kr.entrydsm.observability.adapterin.web.dto.common

import hs.kr.entrydsm.observability.adapterin.web.dto.response.SessionEventResponse
import hs.kr.entrydsm.observability.application.port.`in`.result.SessionEventResult

fun SessionEventResult.toResponse(): SessionEventResponse =
    SessionEventResponse(sessionId = sessionId, heartbeatIntervalSeconds = heartbeatIntervalSeconds)
