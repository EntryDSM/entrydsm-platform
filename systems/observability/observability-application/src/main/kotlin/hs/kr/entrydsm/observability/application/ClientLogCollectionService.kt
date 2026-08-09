package hs.kr.entrydsm.observability.application

import hs.kr.entrydsm.observability.application.port.`in`.ClientLogItem
import hs.kr.entrydsm.observability.application.port.`in`.RecordClientLogUseCase
import hs.kr.entrydsm.observability.application.port.`in`.result.ClientLogAcceptResult
import hs.kr.entrydsm.observability.application.port.out.ClientLogInput
import hs.kr.entrydsm.observability.application.port.out.ClientLogStorePort
import hs.kr.entrydsm.observability.application.port.out.LiveLogPublisherPort
import hs.kr.entrydsm.observability.application.port.out.RateLimitPort
import hs.kr.entrydsm.observability.domain.enum.ErrorCode
import hs.kr.entrydsm.observability.domain.exception.MonitorDomainException
import hs.kr.entrydsm.observability.domain.service.UserAgentParser
import org.springframework.stereotype.Service

@Service
class ClientLogCollectionService(
    private val clientLogStorePort: ClientLogStorePort,
    private val rateLimitPort: RateLimitPort,
    private val liveLogPublisherPort: LiveLogPublisherPort,
) : RecordClientLogUseCase {

    override fun record(sessionId: String, logs: List<ClientLogItem>, userAgent: String?, clientIp: String): ClientLogAcceptResult {
        if (!rateLimitPort.tryAcquire("clientlog:$clientIp", RATE_LIMIT, RATE_LIMIT_WINDOW_SECONDS)) {
            throw MonitorDomainException(ErrorCode.TOO_MANY_REQUESTS)
        }
        if (logs.isEmpty() || logs.size > MAX_BATCH_SIZE) {
            throw MonitorDomainException(ErrorCode.INVALID_PAYLOAD)
        }
        val browser = UserAgentParser.browser(userAgent)
        val os = UserAgentParser.os(userAgent)
        logs.forEach { item ->
            val input = ClientLogInput(
                level = item.level,
                source = item.source,
                message = item.message.take(MAX_MESSAGE_LENGTH),
                pageUrl = item.pageUrl,
                browser = browser,
                os = os,
                occurredAt = item.occurredAt,
            )
            clientLogStorePort.record(input)
            liveLogPublisherPort.publishClientLog(input)
        }
        return ClientLogAcceptResult(accepted = logs.size, rejected = 0)
    }

    companion object {
        private const val MAX_BATCH_SIZE = 20
        private const val MAX_MESSAGE_LENGTH = 500
        private const val RATE_LIMIT = 60L
        private const val RATE_LIMIT_WINDOW_SECONDS = 60L
    }
}
