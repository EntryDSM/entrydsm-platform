package hs.kr.entrydsm.observability.adapterout.log

import hs.kr.entrydsm.observability.adapterout.redis.FingerprintLogStore
import hs.kr.entrydsm.observability.application.port.out.ServerLogEntry
import hs.kr.entrydsm.observability.application.port.out.ServerLogPage
import hs.kr.entrydsm.observability.application.port.out.ServerLogStorePort
import hs.kr.entrydsm.observability.application.port.out.StatusFilter
import hs.kr.entrydsm.observability.domain.enum.ServiceName
import hs.kr.entrydsm.observability.domain.model.Cursor
import java.time.Instant
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component

@Component
class RedisServerLogStoreAdapter(redis: StringRedisTemplate) : ServerLogStorePort {
    private val store = FingerprintLogStore(redis, "monitor:server-log")

    override fun list(
        from: Instant,
        to: Instant,
        service: ServiceName?,
        status: StatusFilter?,
        cursor: Cursor?,
        size: Int,
    ): ServerLogPage {
        val group = group(service, status)
        val (fingerprints, hasNext) = store.page(group, from, to, cursor, size)
        val items = fingerprints.mapNotNull { fingerprint -> toEntry(fingerprint, store.entry(fingerprint)) }
        return ServerLogPage(
            totalCount = store.count(group, from, to),
            items = items,
            nextCursor = items.lastOrNull()?.let { Cursor(it.lastOccurredAt.toEpochMilli(), it.fingerprint) },
            hasNext = hasNext,
        )
    }

    private fun group(service: ServiceName?, status: StatusFilter?): String {
        val statusGroup = when (status) {
            is StatusFilter.Exact -> status.code.toString()
            is StatusFilter.StatusClass -> "${status.leadingDigit}xx"
            null -> null
        }
        return when {
            service != null && statusGroup != null -> "service:${service.name}:status:$statusGroup"
            service != null -> "service:${service.name}"
            statusGroup != null -> "status:$statusGroup"
            else -> FingerprintLogStore.ALL
        }
    }

    private fun toEntry(fingerprint: String, fields: Map<String, String>): ServerLogEntry? = runCatching {
        ServerLogEntry(
            fingerprint = fingerprint,
            service = ServiceName.valueOf(fields.getValue("service")),
            method = fields.getValue("method"),
            path = fields.getValue("path"),
            status = fields.getValue("status").toInt(),
            code = fields.getValue("code"),
            grpcStatus = fields["grpcStatus"]?.takeIf(String::isNotEmpty),
            message = fields.getValue("message"),
            count = fields.getValue(FingerprintLogStore.FIELD_COUNT).toLong(),
            firstOccurredAt = Instant.ofEpochMilli(fields.getValue(FingerprintLogStore.FIELD_FIRST_OCCURRED_AT).toLong()),
            lastOccurredAt = Instant.ofEpochMilli(fields.getValue(FingerprintLogStore.FIELD_LAST_OCCURRED_AT).toLong()),
        )
    }.getOrNull()
}
