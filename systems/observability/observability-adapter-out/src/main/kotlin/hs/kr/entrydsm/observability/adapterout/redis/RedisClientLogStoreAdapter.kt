package hs.kr.entrydsm.observability.adapterout.redis

import hs.kr.entrydsm.observability.application.port.out.ClientLogEntry
import hs.kr.entrydsm.observability.application.port.out.ClientLogInput
import hs.kr.entrydsm.observability.application.port.out.ClientLogPage
import hs.kr.entrydsm.observability.application.port.out.ClientLogStorePort
import hs.kr.entrydsm.observability.domain.enum.LogLevel
import hs.kr.entrydsm.observability.domain.enum.LogSource
import hs.kr.entrydsm.observability.domain.model.Cursor
import hs.kr.entrydsm.observability.domain.service.Fingerprint
import java.time.Instant
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component

@Component
class RedisClientLogStoreAdapter(
    redis: StringRedisTemplate,
) : ClientLogStorePort {
    private val store = FingerprintLogStore(redis, "monitor:clientlog")

    override fun record(input: ClientLogInput) {
        val fingerprint = Fingerprint.of(input.source.name, input.message, input.pageUrl)
        store.upsert(
            fingerprint = fingerprint,
            occurredAt = input.occurredAt,
            fields = mapOf(
                FIELD_LEVEL to input.level.name,
                FIELD_SOURCE to input.source.name,
                FIELD_MESSAGE to input.message,
                FIELD_PAGE_URL to input.pageUrl,
                FIELD_BROWSER to (input.browser ?: ""),
                FIELD_OS to (input.os ?: ""),
            ),
            groupKeys = listOf(input.level.name),
        )
    }

    override fun countByLevel(from: Instant, to: Instant): Map<LogLevel, Long> =
        LogLevel.entries.associateWith { store.count(it.name, from, to) }

    override fun list(from: Instant, to: Instant, levels: Set<LogLevel>?, cursor: Cursor?, size: Int): ClientLogPage {
        val group = if (levels?.size == 1) levels.first().name else FingerprintLogStore.ALL
        val (fingerprints, hasNext) = store.page(group, from, to, cursor, size)
        val items = fingerprints.mapNotNull { toEntry(it, store.entry(it)) }
            .let { entries -> if (levels != null && levels.size > 1) entries.filter { it.level in levels } else entries }
        val counts = countByLevel(from, to)
        return ClientLogPage(
            totalCount = counts.values.sum(),
            errorCount = counts[LogLevel.ERROR] ?: 0,
            warnCount = counts[LogLevel.WARN] ?: 0,
            items = items,
            nextCursor = items.lastOrNull()?.let { Cursor(it.lastOccurredAt.toEpochMilli(), it.fingerprint) },
            hasNext = hasNext,
        )
    }

    /** 예전에 기록된 값이 지금의 enum에 없을 수 있다. 항목 하나 때문에 목록 조회 전체가 실패하지 않도록 건너뛴다. */
    private fun toEntry(fingerprint: String, fields: Map<String, String>): ClientLogEntry? {
        if (fields.isEmpty()) return null
        val level = LogLevel.entries.find { it.name == fields[FIELD_LEVEL] } ?: return null
        val source = LogSource.entries.find { it.name == fields[FIELD_SOURCE] } ?: return null
        return ClientLogEntry(
            fingerprint = fingerprint,
            level = level,
            message = fields.getValue(FIELD_MESSAGE),
            source = source,
            pageUrl = fields.getValue(FIELD_PAGE_URL),
            browser = fields[FIELD_BROWSER]?.takeIf { it.isNotEmpty() },
            os = fields[FIELD_OS]?.takeIf { it.isNotEmpty() },
            count = fields.getValue(FingerprintLogStore.FIELD_COUNT).toLong(),
            firstOccurredAt = Instant.ofEpochMilli(fields.getValue(FingerprintLogStore.FIELD_FIRST_OCCURRED_AT).toLong()),
            lastOccurredAt = Instant.ofEpochMilli(fields.getValue(FingerprintLogStore.FIELD_LAST_OCCURRED_AT).toLong()),
        )
    }

    companion object {
        private const val FIELD_LEVEL = "level"
        private const val FIELD_SOURCE = "source"
        private const val FIELD_MESSAGE = "message"
        private const val FIELD_PAGE_URL = "pageUrl"
        private const val FIELD_BROWSER = "browser"
        private const val FIELD_OS = "os"
    }
}
