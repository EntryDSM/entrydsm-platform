package hs.kr.entrydsm.observability.adapterout.redis

import hs.kr.entrydsm.observability.application.port.out.RateLimitPort
import java.time.Duration
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component

/** IP 기준 고정 윈도우 카운터. 이미 Redis를 쓰므로 별도 rate-limit 라이브러리 없이 INCR+EXPIRE로 구현한다. */
@Component
class RedisRateLimitAdapter(
    private val redis: StringRedisTemplate,
) : RateLimitPort {
    override fun tryAcquire(key: String, limit: Long, windowSeconds: Long): Boolean {
        val bucket = System.currentTimeMillis() / (windowSeconds * 1000)
        val redisKey = "monitor:ratelimit:$key:$bucket"
        val count = redis.opsForValue().increment(redisKey) ?: 1L
        if (count == 1L) {
            redis.expire(redisKey, Duration.ofSeconds(windowSeconds))
        }
        return count <= limit
    }
}
