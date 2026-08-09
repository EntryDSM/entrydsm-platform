package hs.kr.entrydsm.observability.application.port.`in`

import hs.kr.entrydsm.observability.application.port.`in`.result.StorageUsageResult

interface GetStorageUsageUseCase {
    fun getUsage(): StorageUsageResult
}
