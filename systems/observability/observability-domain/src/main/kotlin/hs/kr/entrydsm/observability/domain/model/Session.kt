package hs.kr.entrydsm.observability.domain.model

import hs.kr.entrydsm.observability.domain.enum.ServiceName
import java.time.Instant

data class Session(
    val sessionId: String,
    val service: ServiceName,
    val enteredAt: Instant,
    val lastHeartbeatAt: Instant,
) {
    fun heartbeat(now: Instant, service: ServiceName): Session =
        copy(service = service, lastHeartbeatAt = now)

    fun isExpired(now: Instant, windowSeconds: Long): Boolean =
        lastHeartbeatAt.plusSeconds(windowSeconds).isBefore(now)
}
