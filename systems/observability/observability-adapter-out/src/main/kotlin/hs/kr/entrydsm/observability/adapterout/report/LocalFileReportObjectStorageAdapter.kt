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

    /** 저장 객체는 토큰 이름으로만 만든다. 같은 회차·날짜·형식의 리포트가 서로를 덮어써 이전 토큰이 다른 내용을 내려주는 일을 막고, fileName이 경로로 해석될 여지도 없앤다. */
    override fun store(fileName: String, bytes: ByteArray): StoredReport {
        val dir = File(storageDir).apply { mkdirs() }
        val token = UUID.randomUUID().toString()
        val objectFile = File(dir, token)
        objectFile.writeBytes(bytes)

        redis.opsForValue().set(tokenKey(token), "$fileName\n${objectFile.absolutePath}", TTL)
        return StoredReport(
            downloadUrl = "/api/monitor/v11/reports/download?token=$token",
            expiresAt = Instant.now(clock).plus(TTL),
        )
    }

    override fun resolve(token: String): DownloadedReport? {
        val stored = redis.opsForValue().get(tokenKey(token)) ?: return null
        val (fileName, path) = stored.split("\n", limit = 2).takeIf { it.size == 2 } ?: return null
        val file = File(path)
        if (!file.exists()) return null
        return DownloadedReport(fileName = fileName, bytes = file.readBytes())
    }

    private fun tokenKey(token: String) = "monitor:report:token:$token"

    companion object {
        private val TTL: Duration = Duration.ofMinutes(5)
    }
}
