package hs.kr.entrydsm.observability.application.port.`in`.result

data class SessionEventResult(
    val sessionId: String,
    val heartbeatIntervalSeconds: Int,
)
