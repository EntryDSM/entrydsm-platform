package hs.kr.entrydsm.observability.adapterout.redis

import hs.kr.entrydsm.observability.application.port.out.SessionStorePort
import hs.kr.entrydsm.observability.domain.enum.DeviceType
import hs.kr.entrydsm.observability.domain.enum.ServiceName
import java.time.Duration
import java.time.Instant
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
import org.springframework.stereotype.Component

/**
 * 세션·트래픽 집계를 Redis에 저장한다.
 * ponytail: 다중 인스턴스에서도 Redis가 단일 소스라 별도 확장 작업 없이 그대로 동작한다.
 */
@Component
class RedisSessionStoreAdapter(
    private val redis: StringRedisTemplate,
) : SessionStorePort {

    override fun enter(sessionId: String, service: ServiceName, deviceType: DeviceType, now: Instant) {
        val nowMillis = now.toEpochMilli()
        val hashOps = redis.opsForHash<String, String>()
        hashOps.putAll(
            metaKey(sessionId),
            mapOf(
                FIELD_SERVICE to service.name,
                FIELD_ENTERED_AT to nowMillis.toString(),
                FIELD_LAST_HEARTBEAT_AT to nowMillis.toString(),
            ),
        )
        redis.expire(metaKey(sessionId), SESSION_TTL)
        touchWindow(sessionId, service, nowMillis)
        redis.opsForSet().add(VISITORS_KEY, sessionId)
        hashOps.increment(DEVICE_COUNTS_KEY, deviceType.name, 1)
    }

    /** 세션이 없거나 발급 당시와 다른 서비스로 온 heartbeat는 실패시킨다. 서비스를 갈아타면 같은 세션이 여러 서비스의 동시접속자에 중복 집계된다. */
    override fun heartbeat(sessionId: String, service: ServiceName, now: Instant): Boolean {
        val nowMillis = now.toEpochMilli()
        val touched = redis.execute(
            TOUCH_IF_SAME_SERVICE,
            listOf(metaKey(sessionId)),
            service.name,
            nowMillis.toString(),
            SESSION_TTL.seconds.toString(),
        )
        if (touched != 1L) return false
        touchWindow(sessionId, service, nowMillis)
        return true
    }

    override fun leave(sessionId: String, service: ServiceName, now: Instant): Boolean {
        val meta = redis.opsForHash<String, String>().entries(metaKey(sessionId))
        if (meta.isEmpty()) return false
        // DELETE가 true를 돌려준 호출만 체류시간을 집계한다. 동시 leave 요청이 같은 세션을 중복 집계하지 않도록.
        if (redis.delete(metaKey(sessionId)) != true) return false
        meta[FIELD_ENTERED_AT]?.toLongOrNull()?.let { enteredAt ->
            val durationSeconds = (now.toEpochMilli() - enteredAt) / 1000
            redis.opsForValue().increment(DURATION_SUM_KEY, durationSeconds)
            redis.opsForValue().increment(DURATION_COUNT_KEY)
        }
        ServiceName.entries.forEach { redis.opsForZSet().remove(windowKey(it), sessionId) }
        redis.opsForZSet().remove(ALL_WINDOW_KEY, sessionId)
        return true
    }

    override fun concurrentUsers(service: ServiceName?, now: Instant, windowSeconds: Long): Int {
        val key = service?.let { windowKey(it) } ?: ALL_WINDOW_KEY
        val min = (now.toEpochMilli() - windowSeconds * 1000).toDouble()
        return (redis.opsForZSet().count(key, min, Double.MAX_VALUE) ?: 0L).toInt()
    }

    override fun totalVisitors(): Long = redis.opsForSet().size(VISITORS_KEY) ?: 0L

    override fun avgSessionDurationSeconds(): Long {
        val sum = redis.opsForValue().get(DURATION_SUM_KEY)?.toLongOrNull() ?: 0L
        val count = redis.opsForValue().get(DURATION_COUNT_KEY)?.toLongOrNull() ?: 0L
        return if (count == 0L) 0L else sum / count
    }

    override fun deviceBreakdown(): Map<DeviceType, Long> {
        val entries = redis.opsForHash<String, String>().entries(DEVICE_COUNTS_KEY)
        return DeviceType.entries.associateWith { entries[it.name]?.toLongOrNull() ?: 0L }
    }

    override fun sampleConcurrency(now: Instant, windowSeconds: Long) {
        val current = concurrentUsers(null, now, windowSeconds)
        redis.execute(SET_IF_GREATER, listOf(CONCURRENT_MAX_KEY), current.toString())
        redis.opsForValue().increment(CONCURRENT_SUM_KEY, current.toLong())
        redis.opsForValue().increment(CONCURRENT_SAMPLES_KEY)
    }

    override fun concurrentMax(): Int = redis.opsForValue().get(CONCURRENT_MAX_KEY)?.toIntOrNull() ?: 0

    override fun concurrentAvg(): Int {
        val sum = redis.opsForValue().get(CONCURRENT_SUM_KEY)?.toLongOrNull() ?: 0L
        val samples = redis.opsForValue().get(CONCURRENT_SAMPLES_KEY)?.toLongOrNull() ?: 0L
        return if (samples == 0L) 0 else (sum / samples).toInt()
    }

    private fun touchWindow(sessionId: String, service: ServiceName, nowMillis: Long) {
        redis.opsForZSet().add(windowKey(service), sessionId, nowMillis.toDouble())
        redis.opsForZSet().add(ALL_WINDOW_KEY, sessionId, nowMillis.toDouble())
        redis.expire(windowKey(service), SESSION_TTL)
        redis.expire(ALL_WINDOW_KEY, SESSION_TTL)
    }

    private fun metaKey(sessionId: String) = "monitor:session:meta:$sessionId"

    private fun windowKey(service: ServiceName) = "monitor:session:window:${service.name}"

    companion object {
        private const val VISITORS_KEY = "monitor:visitors:all"
        private const val DEVICE_COUNTS_KEY = "monitor:device:counts"
        private const val DURATION_SUM_KEY = "monitor:duration:sum"
        private const val DURATION_COUNT_KEY = "monitor:duration:count"
        private const val CONCURRENT_MAX_KEY = "monitor:concurrent:max"
        private const val CONCURRENT_SUM_KEY = "monitor:concurrent:sum"
        private const val CONCURRENT_SAMPLES_KEY = "monitor:concurrent:samples"
        private const val ALL_WINDOW_KEY = "monitor:session:window:ALL"
        private const val FIELD_SERVICE = "service"
        private const val FIELD_ENTERED_AT = "enteredAt"
        private const val FIELD_LAST_HEARTBEAT_AT = "lastHeartbeatAt"
        private val SESSION_TTL: Duration = Duration.ofHours(6)

        /** 저장된 서비스가 요청과 같을 때만 heartbeat을 반영한다. 존재 확인과 갱신 사이의 경쟁을 없애려 한 번에 실행한다. */
        private val TOUCH_IF_SAME_SERVICE = DefaultRedisScript(
            """
            local service = redis.call('HGET', KEYS[1], '$FIELD_SERVICE')
            if not service or service ~= ARGV[1] then
                return 0
            end
            redis.call('HSET', KEYS[1], '$FIELD_LAST_HEARTBEAT_AT', ARGV[2])
            redis.call('EXPIRE', KEYS[1], ARGV[3])
            return 1
            """.trimIndent(),
            Long::class.java,
        )

        /** 비교와 갱신 사이에 더 작은 값이 최대치를 덮어쓰지 않도록 한 번에 실행한다. */
        private val SET_IF_GREATER = DefaultRedisScript(
            """
            local current = tonumber(ARGV[1])
            local stored = tonumber(redis.call('GET', KEYS[1]) or '0')
            if current > stored then
                redis.call('SET', KEYS[1], ARGV[1])
            end
            return current
            """.trimIndent(),
            Long::class.java,
        )
    }
}
