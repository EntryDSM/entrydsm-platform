package hs.kr.entrydsm.observability.adapterout.storage

import hs.kr.entrydsm.observability.application.port.out.StorageUsage
import hs.kr.entrydsm.observability.application.port.out.StorageUsagePort
import java.time.Clock
import java.time.Instant
import org.springframework.stereotype.Component

/**
 * ponytail: 이 서비스는 DB 접속 권한도, 실제 S3 접근 권한도 없어 사용량을 재지 못하고 0을 반환한다.
 * 리포트 로컬 디렉터리 크기로 bucket을 대신하던 값은 리포트를 Redis로 옮기면서 뺐다(인스턴스마다 값이 달랐다).
 * 실제 DB/S3 사용량이 필요해지면 각 관측 대상의 admin API를 붙인다.
 */
@Component
class UnmeasuredStorageUsageAdapter(
    private val clock: Clock,
) : StorageUsagePort {
    override fun measure(): StorageUsage = StorageUsage(
        databaseUsedBytes = 0,
        databaseTotalBytes = null,
        bucketUsedBytes = 0,
        bucketObjectCount = 0,
        measuredAt = Instant.now(clock),
    )
}
