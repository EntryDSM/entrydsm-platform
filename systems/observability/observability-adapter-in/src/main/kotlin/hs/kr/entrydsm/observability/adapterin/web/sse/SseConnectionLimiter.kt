package hs.kr.entrydsm.observability.adapterin.web.sse

import java.util.concurrent.ConcurrentHashMap
import org.springframework.stereotype.Component

/**
 * 계정당 SSE 연결 수를 인스턴스별로 제한한다. 목적은 인스턴스 하나의 커넥션 고갈을 막는 것이다.
 * ponytail: 인메모리 카운터라 전체 한도는 인스턴스 수만큼 늘어난다(3대면 계정당 최대 9개). 전역 한도가 필요해지면 Redis 카운터로 옮긴다.
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
