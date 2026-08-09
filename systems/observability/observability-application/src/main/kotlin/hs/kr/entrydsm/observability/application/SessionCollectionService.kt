package hs.kr.entrydsm.observability.application

import hs.kr.entrydsm.observability.application.port.`in`.RecordSessionEventUseCase
import hs.kr.entrydsm.observability.application.port.`in`.result.SessionEventResult
import hs.kr.entrydsm.observability.application.port.out.MetricsStorePort
import hs.kr.entrydsm.observability.application.port.out.RateLimitPort
import hs.kr.entrydsm.observability.application.port.out.SessionStorePort
import hs.kr.entrydsm.observability.domain.enum.ErrorCode
import hs.kr.entrydsm.observability.domain.enum.ServiceName
import hs.kr.entrydsm.observability.domain.enum.SessionEventType
import hs.kr.entrydsm.observability.domain.exception.MonitorDomainException
import hs.kr.entrydsm.observability.domain.service.DeviceTypeParser
import java.time.Clock
import java.time.Instant
import java.util.UUID
import org.springframework.stereotype.Service

@Service
class SessionCollectionService(
    private val sessionStorePort: SessionStorePort,
    private val rateLimitPort: RateLimitPort,
    private val metricsStorePort: MetricsStorePort,
    private val clock: Clock,
) : RecordSessionEventUseCase {

    override fun record(
        event: SessionEventType,
        sessionId: String?,
        service: ServiceName,
        userAgent: String?,
        clientIp: String,
    ): SessionEventResult {
        if (!rateLimitPort.tryAcquire("session:$clientIp", RATE_LIMIT, RATE_LIMIT_WINDOW_SECONDS)) {
            throw MonitorDomainException(ErrorCode.TOO_MANY_REQUESTS)
        }
        val now = Instant.now(clock)
        val resolvedSessionId = when (event) {
            SessionEventType.ENTER -> {
                val newSessionId = generateSessionId()
                sessionStorePort.enter(newSessionId, service, DeviceTypeParser.parse(userAgent), now)
                metricsStorePort.recordVisitor(newSessionId, now)
                newSessionId
            }
            SessionEventType.HEARTBEAT -> {
                val id = sessionId ?: throw MonitorDomainException(ErrorCode.INVALID_PAYLOAD)
                if (!sessionStorePort.heartbeat(id, service, now)) {
                    throw MonitorDomainException(ErrorCode.SESSION_NOT_FOUND)
                }
                metricsStorePort.recordVisitor(id, now)
                id
            }
            SessionEventType.LEAVE -> {
                val id = sessionId ?: throw MonitorDomainException(ErrorCode.INVALID_PAYLOAD)
                if (!sessionStorePort.leave(id, service, now)) {
                    throw MonitorDomainException(ErrorCode.SESSION_NOT_FOUND)
                }
                id
            }
        }
        return SessionEventResult(resolvedSessionId, HEARTBEAT_INTERVAL_SECONDS)
    }

    private fun generateSessionId(): String = "sess_" + UUID.randomUUID().toString().replace("-", "").take(20)

    companion object {
        private const val HEARTBEAT_INTERVAL_SECONDS = 15
        private const val RATE_LIMIT = 60L
        private const val RATE_LIMIT_WINDOW_SECONDS = 60L
    }
}
