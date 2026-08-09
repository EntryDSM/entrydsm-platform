package hs.kr.entrydsm.observability.adapterin.web.controller

import hs.kr.entrydsm.observability.adapterin.web.sse.SseBroadcaster
import hs.kr.entrydsm.observability.adapterin.web.sse.SseConnectionLimiter
import hs.kr.entrydsm.observability.domain.enum.ErrorCode
import hs.kr.entrydsm.observability.domain.exception.MonitorDomainException
import jakarta.servlet.http.HttpServletRequest
import java.util.concurrent.TimeUnit
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

@RestController
class MonitorStreamController(
    private val sseBroadcaster: SseBroadcaster,
    private val connectionLimiter: SseConnectionLimiter,
) {
    @GetMapping("/api/monitor/v11/stream", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun stream(httpRequest: HttpServletRequest): SseEmitter {
        val clientKey = clientIp(httpRequest)
        if (!connectionLimiter.tryAcquire(clientKey)) {
            throw MonitorDomainException(ErrorCode.TOO_MANY_CONNECTIONS)
        }
        val emitter = SseEmitter(TimeUnit.MINUTES.toMillis(EMITTER_TIMEOUT_MINUTES))
        emitter.onCompletion { connectionLimiter.release(clientKey) }
        emitter.onTimeout { connectionLimiter.release(clientKey) }
        emitter.onError { connectionLimiter.release(clientKey) }
        sseBroadcaster.register(emitter)
        return emitter
    }

    private fun clientIp(request: HttpServletRequest): String =
        request.getHeader("X-Forwarded-For")
            ?.substringBefore(",")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: request.remoteAddr

    companion object {
        private const val EMITTER_TIMEOUT_MINUTES = 30L
    }
}
