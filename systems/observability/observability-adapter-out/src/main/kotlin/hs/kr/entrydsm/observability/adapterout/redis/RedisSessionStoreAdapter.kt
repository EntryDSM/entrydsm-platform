package hs.kr.entrydsm.observability.adapterout.redis

import hs.kr.entrydsm.observability.application.port.out.SessionStorePort
import hs.kr.entrydsm.observability.domain.enum.DeviceType
import hs.kr.entrydsm.observability.domain.enum.ServiceName
import java.time.Duration
import java.time.Instant
import org.springframework.data.redis.core.StringRedisTemplate
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

    override fun heartbeat(sessionId: String, service: ServiceName, now: Instant): Boolean {
        if (!redis.hasKey(metaKey(sessionId))) return false
        val nowMillis = now.toEpochMilli()
        val hashOps = redis.opsForHash<String, String>()
        hashOps.put(metaKey(sessionId), FIELD_SERVICE, service.name)
        hashOps.put(metaKey(sessionId), FIELD_LAST_HEARTBEAT_AT, nowMillis.toString())
        redis.expire(metaKey(sessionId), SESSION_TTL)
        touchWindow(sessionId, service, nowMillis)
        return true
    }

    override fun leave(sessionId: String, service: ServiceName, now: Instant): Boolean {
        val meta = redis.opsForHash<String, String>().entries(metaKey(sessionId))
        if (meta.isEmpty()) return false
        meta[FIELD_ENTERED_AT]?.toLongOrNull()?.let { enteredAt ->
            val durationSeconds = (now.toEpochMilli() - enteredAt) / 1000
            redis.opsForValue().increment(DURATION_SUM_KEY, durationSeconds)
            redis.opsForValue().increment(DURATION_COUNT_KEY)
        }
        redis.delete(metaKey(sessionId))
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
        val currentMax = redis.opsForValue().get(CONCURRENT_MAX_KEY)?.toIntOrNull() ?: 0
        if (current > currentMax) {
            redis.opsForValue().set(CONCURRENT_MAX_KEY, current.toString())
        }
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
    }
}
