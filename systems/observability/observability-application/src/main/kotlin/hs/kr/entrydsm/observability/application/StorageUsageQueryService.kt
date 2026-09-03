package hs.kr.entrydsm.observability.application

import hs.kr.entrydsm.observability.application.port.`in`.GetStorageUsageUseCase
import hs.kr.entrydsm.observability.application.port.`in`.result.BucketUsageResult
import hs.kr.entrydsm.observability.application.port.`in`.result.DatabaseUsageResult
import hs.kr.entrydsm.observability.application.port.`in`.result.StorageUsageResult
import hs.kr.entrydsm.observability.application.port.out.StorageUsagePort

/** ponytail: 실시간으로 측정하므로 서버 자체 캐시는 두지 않는다. cacheTtlSeconds는 클라이언트에 권장하는 캐시 기간이다. */
class StorageUsageQueryService(
    private val storageUsagePort: StorageUsagePort,
) : GetStorageUsageUseCase {

    override fun getUsage(): StorageUsageResult {
        val usage = storageUsagePort.measure()
        val ratio = usage.databaseTotalBytes?.takeIf { it > 0 }?.let { usage.databaseUsedBytes.toDouble() / it }
        return StorageUsageResult(
            database = DatabaseUsageResult(usage.databaseUsedBytes, usage.databaseTotalBytes, ratio, usage.measuredAt),
            bucket = BucketUsageResult(usage.bucketUsedBytes, usage.bucketObjectCount, usage.measuredAt),
            cacheTtlSeconds = CACHE_TTL_SECONDS,
        )
    }

    companion object {
        private const val CACHE_TTL_SECONDS = 300
    }
}
