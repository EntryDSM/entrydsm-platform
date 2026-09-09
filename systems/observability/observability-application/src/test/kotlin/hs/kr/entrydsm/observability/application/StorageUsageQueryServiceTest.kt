package hs.kr.entrydsm.observability.application

import hs.kr.entrydsm.observability.application.port.out.StorageUsage
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StorageUsageQueryServiceTest {
    @Test
    fun computesRatioWhenTotalKnown() {
        val service = StorageUsageQueryService {
            StorageUsage(
                databaseUsedBytes = 50,
                databaseTotalBytes = 200,
                bucketUsedBytes = 1024,
                bucketObjectCount = 3,
                measuredAt = Instant.parse("2026-07-28T14:00:00Z"),
            )
        }

        val result = service.getUsage()

        assertEquals(0.25, result.database.usageRatio!!, 0.0001)
        assertEquals(300, result.cacheTtlSeconds)
    }

    @Test
    fun ratioIsNullWhenTotalUnknown() {
        val service = StorageUsageQueryService {
            StorageUsage(
                databaseUsedBytes = 0,
                databaseTotalBytes = null,
                bucketUsedBytes = 0,
                bucketObjectCount = 0,
                measuredAt = Instant.parse("2026-07-28T14:00:00Z"),
            )
        }

        assertNull(service.getUsage().database.usageRatio)
    }
}
