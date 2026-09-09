package hs.kr.entrydsm.observability.adapterout.storage

import hs.kr.entrydsm.observability.application.port.out.StorageUsage
import hs.kr.entrydsm.observability.application.port.out.StorageUsagePort
import java.io.File
import java.time.Clock
import java.time.Instant
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/**
 * ponytail: 이 서비스는 DB 접속 권한도, 실제 S3 접근 권한도 없다.
 * bucket 사용량은 리포트 로컬 저장 디렉터리 크기로 대체하고, database는 배치가 없어 0/null로 반환한다.
 * 실제 DB/S3 사용량이 필요해지면 각 관측 대상의 admin API를 붙인다.
 */
@Component
class LocalDiskStorageUsageAdapter(
    @Value("\${monitor.report.storage-dir}") private val storageDir: String,
    private val clock: Clock,
) : StorageUsagePort {
    override fun measure(): StorageUsage {
        val files = File(storageDir).listFiles()?.filter { it.isFile } ?: emptyList()
        return StorageUsage(
            databaseUsedBytes = 0,
            databaseTotalBytes = null,
            bucketUsedBytes = files.sumOf { it.length() },
            bucketObjectCount = files.size.toLong(),
            measuredAt = Instant.now(clock),
        )
    }
}
