package hs.kr.entrydsm.observability.adapterout.redis

import hs.kr.entrydsm.observability.domain.model.Cursor
import java.time.Duration
import java.time.Instant
import org.springframework.data.redis.core.StringRedisTemplate

/**
 * fingerprint 단위로 묶이는 이벤트 로그(클라이언트/서버 오류)를 Redis Hash+ZSet으로 저장하는 공용 헬퍼.
 * ZSet의 score는 lastOccurredAt(epoch millis), member는 fingerprint.
 * ponytail: 커서 경계에서 동일 밀리초 score가 겹치면 항목이 한 번 더 보일 수 있다. 운영 로그 열람용이라 감내 가능한 수준.
 */
class FingerprintLogStore(
    private val redis: StringRedisTemplate,
    private val keyPrefix: String,
) {
    fun upsert(fingerprint: String, occurredAt: Instant, fields: Map<String, String>, groupKeys: List<String>) {
        val entryKey = entryKey(fingerprint)
        val hashOps = redis.opsForHash<String, String>()
        val nowMillis = occurredAt.toEpochMilli().toString()
        // HINCRBY는 원자적이고 키가 없으면 1을 돌려준다. 존재 확인 후 분기하면 동시 요청이 서로의 누적 카운트를 덮어쓴다.
        val count = hashOps.increment(entryKey, FIELD_COUNT, 1)
        if (count == 1L) {
            hashOps.putAll(entryKey, fields + mapOf(FIELD_FIRST_OCCURRED_AT to nowMillis, FIELD_LAST_OCCURRED_AT to nowMillis))
        } else {
            hashOps.put(entryKey, FIELD_LAST_OCCURRED_AT, nowMillis)
        }
        redis.expire(entryKey, RETENTION)

        val score = occurredAt.toEpochMilli().toDouble()
        val expiredBefore = occurredAt.minus(RETENTION).toEpochMilli().toDouble()
        (listOf(ALL) + groupKeys).forEach { group ->
            redis.opsForZSet().add(indexKey(group), fingerprint, score)
            // entry Hash는 TTL로 사라지므로 index ZSet도 같은 보존 기간으로 잘라내 무한히 커지지 않게 한다.
            redis.opsForZSet().removeRangeByScore(indexKey(group), 0.0, expiredBefore)
        }
    }

    fun entry(fingerprint: String): Map<String, String> = redis.opsForHash<String, String>().entries(entryKey(fingerprint))

    fun count(group: String, from: Instant, to: Instant): Long =
        redis.opsForZSet().count(indexKey(group), from.toEpochMilli().toDouble(), to.toEpochMilli().toDouble()) ?: 0L

    /** @return (최신순 fingerprint 목록, 다음 페이지 존재 여부) */
    fun page(group: String, from: Instant, to: Instant, cursor: Cursor?, size: Int): Pair<List<String>, Boolean> {
        val maxScore = cursor?.lastScore?.toDouble() ?: to.toEpochMilli().toDouble()
        // 커서가 있으면 직전 페이지의 마지막 항목이 한 번 더 섞여 들어오므로 그만큼 더 가져와야 hasNext가 맞는다.
        val limit = size + 1 + if (cursor != null) 1 else 0
        val fetched = redis.opsForZSet()
            .reverseRangeByScore(indexKey(group), from.toEpochMilli().toDouble(), maxScore, 0, limit.toLong())
            ?.toList()
            ?: emptyList()
        val filtered = if (cursor != null) fetched.filterNot { it == cursor.lastId } else fetched
        val hasNext = filtered.size > size
        return filtered.take(size) to hasNext
    }

    private fun entryKey(fingerprint: String) = "$keyPrefix:entry:$fingerprint"

    private fun indexKey(group: String) = "$keyPrefix:index:$group"

    companion object {
        const val ALL = "ALL"
        const val FIELD_COUNT = "count"
        const val FIELD_FIRST_OCCURRED_AT = "firstOccurredAt"
        const val FIELD_LAST_OCCURRED_AT = "lastOccurredAt"

        /** 로그 조회 API가 허용하는 최대 조회 범위(7일)와 맞춘다. */
        private val RETENTION: Duration = Duration.ofDays(7)
    }
}
