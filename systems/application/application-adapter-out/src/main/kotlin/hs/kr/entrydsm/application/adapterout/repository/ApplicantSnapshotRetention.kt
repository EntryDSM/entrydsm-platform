package hs.kr.entrydsm.application.adapterout.repository

import java.time.LocalDateTime
import java.time.ZoneOffset
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.domain.Range
import org.springframework.data.redis.connection.Limit
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class ApplicantSnapshotRetention(
    private val repository: ApplicantStatusOutboxJpaRepository,
    private val redis: StringRedisTemplate,
    @Value("\${entrydsm.application.events.applicant-status-stream:application.applicant-status}") private val stream: String,
    @Value("\${entrydsm.application.events.snapshot-retention-days:7}") private val retentionDays: Long,
) {
    @Scheduled(fixedDelayString = "\${entrydsm.application.events.snapshot-cleanup-delay-ms:60000}")
    fun cleanup() {
        try {
            require(retentionDays > 0)
            val before = LocalDateTime.now(ZoneOffset.UTC).minusDays(retentionDays)
            repository.deletePublishedBefore(before)
            val ops = redis.opsForStream<String, String>()
            val groups = ops.groups(stream).toList()
            if (groups.isEmpty()) return
            val boundaries = groups.map { group ->
                val pending = ops.pending(stream, group.groupName())
                if (pending.totalPendingMessages > 0) pending.minMessageId() else group.lastDeliveredId()
            }
            val records = ops.range(stream, Range.closed("0-0", "${before.toInstant(ZoneOffset.UTC).toEpochMilli()}-0"), Limit.limit().count(100)).orEmpty()
            val safe = records.filter { record -> boundaries.all { compareIds(record.id.value, it) < 0 } }
            if (safe.isNotEmpty()) ops.delete(stream, *safe.map { it.id }.toTypedArray())
            // ponytail: 미확인 이벤트는 유실 방지를 위해 유지한다. 오래 멈춘 소비자는 운영자가 복구한다.
            if (safe.size < records.size) LoggerFactory.getLogger(javaClass).warn("Snapshot retention blocked by unacknowledged consumer events")
        } catch (exception: Exception) {
            LoggerFactory.getLogger(javaClass).error("Applicant snapshot retention failed", exception)
        }
    }
    private fun compareIds(left: String, right: String): Int {
        val a = left.split('-').map(String::toLong)
        val b = right.split('-').map(String::toLong)
        return a[0].compareTo(b[0]).takeIf { it != 0 } ?: a[1].compareTo(b[1])
    }
}
