package hs.kr.entrydsm.observability.adapterout.report

import hs.kr.entrydsm.observability.application.port.out.DownloadedReport
import hs.kr.entrydsm.observability.application.port.out.ReportObjectStoragePort
import hs.kr.entrydsm.observability.application.port.out.StoredReport
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.Base64
import java.util.UUID
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component

/**
 * S3 presigned URL 자리를 Redis 만료 토큰으로 대체한다.
 * 리포트를 만든 인스턴스와 다운로드 요청을 받는 인스턴스가 다를 수 있어 파일 대신 내용을 Redis에 둔다.
 * 대시보드 스냅샷 한 장 분량이라 작고, 토큰과 함께 만료되어 따로 치울 것이 없다.
 * 나중에 실제 S3로 교체할 때는 이 어댑터만 바꾸면 된다(포트는 그대로).
 */
@Component
class RedisReportObjectStorageAdapter(
    private val redis: StringRedisTemplate,
    private val clock: Clock,
) : ReportObjectStoragePort {

    /** 토큰마다 따로 저장해 같은 회차·날짜·형식의 리포트가 서로를 덮어써 이전 토큰이 다른 내용을 내려주는 일을 막는다. */
    override fun store(fileName: String, bytes: ByteArray): StoredReport {
        val token = UUID.randomUUID().toString()
        redis.opsForValue().set(contentKey(token), "$fileName\n${Base64.getEncoder().encodeToString(bytes)}", TTL)
        return StoredReport(
            downloadUrl = "/api/monitor/v11/reports/download?token=$token",
            expiresAt = Instant.now(clock).plus(TTL),
        )
    }

    override fun resolve(token: String): DownloadedReport? {
        val stored = redis.opsForValue().get(contentKey(token)) ?: return null
        val (fileName, content) = stored.split("\n", limit = 2).takeIf { it.size == 2 } ?: return null
        return DownloadedReport(fileName = fileName, bytes = Base64.getDecoder().decode(content))
    }

    private fun contentKey(token: String) = "monitor:report:content:$token"

    companion object {
        private val TTL: Duration = Duration.ofMinutes(5)
    }
}
