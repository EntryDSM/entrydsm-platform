package hs.kr.entrydsm.observability.adapterin.web.sse

import java.util.concurrent.ConcurrentHashMap
import org.springframework.stereotype.Component

/**
 * ponytail: 단일 인스턴스 가정의 인메모리 카운터. 인증 붙기 전이라 계정 대신 IP 기준으로 제한한다.
 * JWT 인증이 연결되면 계정 단위로 교체한다.
 */
@Component
class SseConnectionLimiter {
    private val counts = ConcurrentHashMap<String, Int>()

    fun tryAcquire(key: String): Boolean {
        // compute는 키 단위로 원자적이다. 읽고 쓰는 사이에 다른 요청이 끼어들어 제한을 넘기지 않는다.
        var acquired = false
        counts.compute(key) { _, current ->
            val count = current ?: 0
            if (count < MAX_CONNECTIONS_PER_KEY) {
                acquired = true
                count + 1
            } else {
                count
            }
        }
        return acquired
    }

    fun release(key: String) {
        // 마지막 연결이 끊기면 키까지 지운다. 남겨두면 IP 수만큼 맵이 계속 커진다.
        counts.computeIfPresent(key) { _, count -> (count - 1).takeIf { it > 0 } }
    }

    companion object {
        private const val MAX_CONNECTIONS_PER_KEY = 3
    }
}
