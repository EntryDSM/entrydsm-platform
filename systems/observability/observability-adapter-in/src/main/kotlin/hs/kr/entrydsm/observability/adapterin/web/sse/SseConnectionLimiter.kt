package hs.kr.entrydsm.observability.adapterin.web.sse

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import org.springframework.stereotype.Component

/**
 * ponytail: 단일 인스턴스 가정의 인메모리 카운터. 인증 붙기 전이라 계정 대신 IP 기준으로 제한한다.
 * JWT 인증이 연결되면 계정 단위로 교체한다.
 */
@Component
class SseConnectionLimiter {
    private val counts = ConcurrentHashMap<String, AtomicInteger>()

    fun tryAcquire(key: String): Boolean {
        val count = counts.computeIfAbsent(key) { AtomicInteger(0) }
        if (count.incrementAndGet() <= MAX_CONNECTIONS_PER_KEY) return true
        count.decrementAndGet()
        return false
    }

    fun release(key: String) {
        counts[key]?.decrementAndGet()
    }

    companion object {
        private const val MAX_CONNECTIONS_PER_KEY = 3
    }
}
