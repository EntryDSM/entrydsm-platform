package hs.kr.entrydsm.observability.adapterout.redis

import hs.kr.entrydsm.observability.domain.model.Cursor
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
        if (redis.hasKey(entryKey)) {
            hashOps.increment(entryKey, FIELD_COUNT, 1)
            hashOps.put(entryKey, FIELD_LAST_OCCURRED_AT, nowMillis)
        } else {
            hashOps.putAll(
                entryKey,
                fields + mapOf(FIELD_COUNT to "1", FIELD_FIRST_OCCURRED_AT to nowMillis, FIELD_LAST_OCCURRED_AT to nowMillis),
            )
        }
        val score = occurredAt.toEpochMilli().toDouble()
        redis.opsForZSet().add(indexKey(ALL), fingerprint, score)
        groupKeys.forEach { redis.opsForZSet().add(indexKey(it), fingerprint, score) }
    }

    fun entry(fingerprint: String): Map<String, String> = redis.opsForHash<String, String>().entries(entryKey(fingerprint))

    fun count(group: String, from: Instant, to: Instant): Long =
        redis.opsForZSet().count(indexKey(group), from.toEpochMilli().toDouble(), to.toEpochMilli().toDouble()) ?: 0L

    /** @return (최신순 fingerprint 목록, 다음 페이지 존재 여부) */
    fun page(group: String, from: Instant, to: Instant, cursor: Cursor?, size: Int): Pair<List<String>, Boolean> {
        val maxScore = cursor?.lastScore?.toDouble() ?: to.toEpochMilli().toDouble()
        val fetched = redis.opsForZSet()
            .reverseRangeByScore(indexKey(group), from.toEpochMilli().toDouble(), maxScore, 0, (size + 1).toLong())
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
    }
}
