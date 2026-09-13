package hs.kr.entrydsm.observability.adapterin.web.controller

import hs.kr.entrydsm.observability.adapterin.web.security.JwtAuthInterceptor
import hs.kr.entrydsm.observability.adapterin.web.sse.SseBroadcaster
import hs.kr.entrydsm.observability.adapterin.web.sse.SseConnectionLimiter
import hs.kr.entrydsm.observability.domain.enum.ErrorCode
import hs.kr.entrydsm.observability.domain.exception.MonitorDomainException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

@RestController
class MonitorStreamController(
    private val sseBroadcaster: SseBroadcaster,
    private val connectionLimiter: SseConnectionLimiter,
) {
    // 관리자들이 학교 NAT 같은 IP 하나를 함께 쓸 수 있어 IP가 아니라 계정 단위로 제한한다.
    @GetMapping("/api/monitor/v11/stream", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun stream(@RequestHeader(JwtAuthInterceptor.USER_ID_HEADER) userId: String): SseEmitter {
        if (!connectionLimiter.tryAcquire(userId)) {
            throw MonitorDomainException(ErrorCode.TOO_MANY_CONNECTIONS)
        }
        val emitter = SseEmitter(TimeUnit.MINUTES.toMillis(EMITTER_TIMEOUT_MINUTES))
        // timeout/error 뒤에도 onCompletion이 불릴 수 있다. 중복 해제되면 카운터가 음수가 되어 제한이 무의미해진다.
        val released = AtomicBoolean(false)
        val releaseOnce = Runnable { if (released.compareAndSet(false, true)) connectionLimiter.release(userId) }
        emitter.onCompletion(releaseOnce)
        emitter.onTimeout(releaseOnce)
        emitter.onError { releaseOnce.run() }
        sseBroadcaster.register(emitter)
        return emitter
    }

    companion object {
        private const val EMITTER_TIMEOUT_MINUTES = 30L
    }
}
