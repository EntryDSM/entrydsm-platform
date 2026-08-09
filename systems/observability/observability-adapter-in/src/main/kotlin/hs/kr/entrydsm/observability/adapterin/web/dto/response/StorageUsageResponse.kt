package hs.kr.entrydsm.observability.adapterin.web.dto.response

import java.time.Instant

data class StorageUsageResponse(
    val database: DatabaseUsageResponse,
    val bucket: BucketUsageResponse,
    val cacheTtlSeconds: Int,
)

data class DatabaseUsageResponse(
    val usedBytes: Long,
    val totalBytes: Long?,
    val usageRatio: Double?,
    val measuredAt: Instant,
)

data class BucketUsageResponse(
    val usedBytes: Long,
    val objectCount: Long,
    val measuredAt: Instant,
)
