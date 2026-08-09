package hs.kr.entrydsm.observability.application.port.out

import java.time.Instant

fun interface StorageUsagePort {
    fun measure(): StorageUsage
}

data class StorageUsage(
    val databaseUsedBytes: Long,
    val databaseTotalBytes: Long?,
    val bucketUsedBytes: Long,
    val bucketObjectCount: Long,
    val measuredAt: Instant,
)
