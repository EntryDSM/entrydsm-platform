package hs.kr.entrydsm.observability.adapterout.redis

import hs.kr.entrydsm.observability.application.port.out.RateLimitPort
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
import org.springframework.stereotype.Component

/** IP 기준 고정 윈도우 카운터. 이미 Redis를 쓰므로 별도 rate-limit 라이브러리 없이 INCR+EXPIRE로 구현한다. */
@Component
class RedisRateLimitAdapter(
    private val redis: StringRedisTemplate,
) : RateLimitPort {
    override fun tryAcquire(key: String, limit: Long, windowSeconds: Long): Boolean {
        val bucket = System.currentTimeMillis() / (windowSeconds * 1000)
        val redisKey = "monitor:ratelimit:$key:$bucket"
        val count = redis.execute(INCREMENT_WITH_TTL, listOf(redisKey), windowSeconds.toString()) ?: 1L
        return count <= limit
    }

    companion object {
        /** INCR 직후 프로세스가 죽어도 TTL 없는 키가 남지 않도록 첫 증가와 EXPIRE를 한 번에 실행한다. */
        private val INCREMENT_WITH_TTL = DefaultRedisScript(
            """
            local count = redis.call('INCR', KEYS[1])
            if count == 1 then
                redis.call('EXPIRE', KEYS[1], ARGV[1])
            end
            return count
            """.trimIndent(),
            Long::class.java,
        )
    }
}
