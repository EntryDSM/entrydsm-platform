package hs.kr.entrydsm.observability.application.port.`in`.result

import java.time.Instant

data class StorageUsageResult(
    val database: DatabaseUsageResult,
    val bucket: BucketUsageResult,
    val cacheTtlSeconds: Int,
)

data class DatabaseUsageResult(
    val usedBytes: Long,
    val totalBytes: Long?,
    val usageRatio: Double?,
    val measuredAt: Instant,
)

data class BucketUsageResult(
    val usedBytes: Long,
    val objectCount: Long,
    val measuredAt: Instant,
)
