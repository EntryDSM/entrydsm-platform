package hs.kr.entrydsm.observability.adapterin.web.dto.response

data class SessionEventResponse(
    val sessionId: String,
    val heartbeatIntervalSeconds: Int,
)
