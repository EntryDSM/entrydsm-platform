package hs.kr.entrydsm.observability.adapterout.report

import hs.kr.entrydsm.observability.application.port.out.DownloadedReport
import hs.kr.entrydsm.observability.application.port.out.ReportObjectStoragePort
import hs.kr.entrydsm.observability.application.port.out.StoredReport
import java.io.File
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component

/**
 * S3 presigned URL 자리를 로컬 디스크 + Redis 만료 토큰으로 대체한다.
 * 나중에 실제 S3로 교체할 때는 이 어댑터만 바꾸면 된다(포트는 그대로).
 */
@Component
class LocalFileReportObjectStorageAdapter(
    @Value("\${monitor.report.storage-dir}") private val storageDir: String,
    private val redis: StringRedisTemplate,
    private val clock: Clock,
) : ReportObjectStoragePort {

    override fun store(fileName: String, bytes: ByteArray): StoredReport {
        val dir = File(storageDir).apply { mkdirs() }
        File(dir, fileName).writeBytes(bytes)

        val token = UUID.randomUUID().toString()
        redis.opsForValue().set(tokenKey(token), File(dir, fileName).absolutePath, TTL)
        return StoredReport(
            downloadUrl = "/api/monitor/v11/reports/download?token=$token",
            expiresAt = Instant.now(clock).plus(TTL),
        )
    }

    override fun resolve(token: String): DownloadedReport? {
        val path = redis.opsForValue().get(tokenKey(token)) ?: return null
        val file = File(path)
        if (!file.exists()) return null
        return DownloadedReport(fileName = file.name, bytes = file.readBytes())
    }

    private fun tokenKey(token: String) = "monitor:report:token:$token"

    companion object {
        private val TTL: Duration = Duration.ofMinutes(5)
    }
}
