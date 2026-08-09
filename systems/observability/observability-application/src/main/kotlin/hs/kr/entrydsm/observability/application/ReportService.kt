package hs.kr.entrydsm.observability.application

import hs.kr.entrydsm.observability.application.port.`in`.GenerateReportUseCase
import hs.kr.entrydsm.observability.application.port.`in`.GetDashboardSnapshotUseCase
import hs.kr.entrydsm.observability.application.port.`in`.result.ReportResult
import hs.kr.entrydsm.observability.application.port.out.ReportGeneratorPort
import hs.kr.entrydsm.observability.application.port.out.ReportObjectStoragePort
import hs.kr.entrydsm.observability.application.port.out.RoundPort
import hs.kr.entrydsm.observability.domain.enum.ReportFormat
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import org.springframework.stereotype.Service

/** ponytail: 데이터량이 적어 동기 생성만 지원한다(202 GENERATING/폴링 큐 없음). 느려지면 잡 큐로 교체. */
@Service
class ReportService(
    private val getDashboardSnapshotUseCase: GetDashboardSnapshotUseCase,
    private val reportGeneratorPort: ReportGeneratorPort,
    private val reportObjectStoragePort: ReportObjectStoragePort,
    private val roundPort: RoundPort,
    private val clock: Clock,
) : GenerateReportUseCase {

    override fun generate(format: ReportFormat): ReportResult {
        val snapshot = getDashboardSnapshotUseCase.getSnapshot(null)
        val bytes = reportGeneratorPort.generate(format, snapshot)
        val round = roundPort.current()
        val dateStamp = DATE_FORMATTER.format(Instant.now(clock).atZone(ZONE))
        val fileName = "entrymonitor_${round.name}_$dateStamp.${format.name.lowercase()}"
        val stored = reportObjectStoragePort.store(fileName, bytes)
        return ReportResult(
            downloadUrl = stored.downloadUrl,
            fileName = fileName,
            sizeBytes = bytes.size.toLong(),
            expiresAt = stored.expiresAt,
        )
    }

    companion object {
        private val ZONE: ZoneId = ZoneId.of("Asia/Seoul")
        private val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")
    }
}
