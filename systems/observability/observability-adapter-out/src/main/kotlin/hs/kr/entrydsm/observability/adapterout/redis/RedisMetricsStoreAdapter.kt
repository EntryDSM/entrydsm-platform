package hs.kr.entrydsm.observability.adapterout.redis

import hs.kr.entrydsm.observability.application.port.out.MetricsStorePort
import java.time.Duration
import java.time.Instant
import java.util.UUID
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component

/**
 * 방문자 수를 항상 5분 단위(GRANULARITY)로 기록해두고, 조회 시 요청된 interval에 맞춰
 * 여러 5분 버킷을 합집합(SUNIONSTORE)해 고유 방문자 수를 계산한다.
 * ponytail: 넓은 범위(예: 90일 x 1d)를 조회하면 유니온 대상 키가 많아질 수 있다.
 * 관리자 조회용 API라 빈도가 낮아 감내 가능, 느려지면 인터벌별 사전 집계로 교체한다.
 */
@Component
class RedisMetricsStoreAdapter(
    private val redis: StringRedisTemplate,
) : MetricsStorePort {

    override fun recordVisitor(sessionId: String, at: Instant) {
        val key = bucketKey(bucketStart(at))
        redis.opsForSet().add(key, sessionId)
        redis.expire(key, TTL)
    }

    override fun visitorCount(from: Instant, to: Instant): Long {
        val keys = bucketsBetween(from, to).map { bucketKey(it) }
        if (keys.isEmpty()) return 0L
        if (keys.size == 1) return redis.opsForSet().size(keys.first()) ?: 0L

        val tempKey = "monitor:metric:visitor:tmp:${UUID.randomUUID()}"
        return try {
            redis.opsForSet().unionAndStore(keys.first(), keys.drop(1), tempKey)
            redis.opsForSet().size(tempKey) ?: 0L
        } finally {
            redis.delete(tempKey)
        }
    }

    private fun bucketStart(at: Instant): Instant {
        val granularityMillis = GRANULARITY.toMillis()
        return Instant.ofEpochMilli(at.toEpochMilli() / granularityMillis * granularityMillis)
    }

    private fun bucketsBetween(from: Instant, to: Instant): List<Instant> {
        val buckets = mutableListOf<Instant>()
        var cursor = bucketStart(from)
        while (cursor.isBefore(to)) {
            buckets.add(cursor)
            cursor = cursor.plus(GRANULARITY)
        }
        return buckets
    }

    private fun bucketKey(bucketStart: Instant) = "monitor:metric:visitor:${bucketStart.toEpochMilli()}"

    companion object {
        private val GRANULARITY: Duration = Duration.ofMinutes(5)
        private val TTL: Duration = Duration.ofDays(91)
    }
}
