package hs.kr.entrydsm.observability.adapterout.storage

import hs.kr.entrydsm.observability.adapterout.report.RedisReportObjectStorageAdapter
import hs.kr.entrydsm.observability.application.port.out.StorageUsage
import hs.kr.entrydsm.observability.application.port.out.StorageUsagePort
import java.time.Clock
import java.time.Instant
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component

@Component
class RedisStorageUsageAdapter(
    private val redis: StringRedisTemplate,
    private val clock: Clock,
) : StorageUsagePort {
    override fun measure(): StorageUsage {
        val now = Instant.now(clock)
        val expired = redis.opsForZSet().rangeByScore(
            RedisReportObjectStorageAdapter.REPORT_EXPIRY_KEY,
            0.0,
            now.toEpochMilli().toDouble(),
        ).orEmpty()
        if (expired.isNotEmpty()) {
            redis.opsForHash<String, String>().delete(RedisReportObjectStorageAdapter.REPORT_SIZE_KEY, *expired.toTypedArray())
            redis.opsForZSet().remove(RedisReportObjectStorageAdapter.REPORT_EXPIRY_KEY, *expired.toTypedArray())
        }
        val sizes = redis.opsForHash<String, String>().values(RedisReportObjectStorageAdapter.REPORT_SIZE_KEY)
            .mapNotNull(String::toLongOrNull)
        val memory = redis.execute { connection -> connection.serverCommands().info("memory") }
        return StorageUsage(
            databaseUsedBytes = memory?.getProperty("used_memory")?.toLongOrNull() ?: 0,
            databaseTotalBytes = memory?.getProperty("maxmemory")?.toLongOrNull()?.takeIf { it > 0 },
            bucketUsedBytes = sizes.sum(),
            bucketObjectCount = sizes.size.toLong(),
            measuredAt = now,
        )
    }
}
