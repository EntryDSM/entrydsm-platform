package hs.kr.entrydsm.observability.application

import hs.kr.entrydsm.observability.application.port.`in`.GetDashboardSnapshotUseCase
import hs.kr.entrydsm.observability.application.port.`in`.result.ApiStatsResult
import hs.kr.entrydsm.observability.application.port.`in`.result.BusinessStatsResult
import hs.kr.entrydsm.observability.application.port.`in`.result.ClientLogCountResult
import hs.kr.entrydsm.observability.application.port.`in`.result.ConcurrentResult
import hs.kr.entrydsm.observability.application.port.`in`.result.DashboardSnapshotResult
import hs.kr.entrydsm.observability.application.port.`in`.result.OutcomeCountResult
import hs.kr.entrydsm.observability.application.port.`in`.result.PeriodResult
import hs.kr.entrydsm.observability.application.port.`in`.result.ResourceUsageBriefResult
import hs.kr.entrydsm.observability.application.port.`in`.result.ServiceActivityResult
import hs.kr.entrydsm.observability.application.port.`in`.result.TrafficResult
import hs.kr.entrydsm.observability.application.port.out.Round
import hs.kr.entrydsm.observability.application.port.out.StoredReport
import hs.kr.entrydsm.observability.domain.enum.ReportFormat
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportServiceTest {
    private val clock = Clock.fixed(Instant.parse("2026-07-28T14:03:11Z"), ZoneOffset.UTC)
    private val snapshot = DashboardSnapshotResult(
        generatedAt = Instant.now(clock),
        period = PeriodResult("ADMISSION", "2026-1", Instant.EPOCH, Instant.EPOCH),
        traffic = TrafficResult(0, ConcurrentResult(0, 0, 0), 0, emptyList()),
        api = ApiStatsResult(0, 0, 0, 0.0),
        business = BusinessStatsResult(OutcomeCountResult(0, 0), OutcomeCountResult(0, 0)),
        services = ServiceActivityResult(30, emptyList()),
        clientLog = ClientLogCountResult(0, 0),
        resource = ResourceUsageBriefResult(0, 0, Instant.now(clock)),
    )

    @Test
    fun buildsFileNameFromRoundAndDate() {
        val service = ReportService(
            getDashboardSnapshotUseCase = GetDashboardSnapshotUseCase { snapshot },
            reportGeneratorPort = { _, _ -> byteArrayOf(1, 2, 3) },
            reportObjectStoragePort = object : hs.kr.entrydsm.observability.application.port.out.ReportObjectStoragePort {
                override fun store(fileName: String, bytes: ByteArray) =
                    StoredReport(downloadUrl = "/api/monitor/v11/reports/download?token=t", expiresAt = Instant.now(clock).plusSeconds(300))
                override fun resolve(token: String) = null
            },
            roundPort = { Round("2026-1", Instant.EPOCH, Instant.EPOCH) },
            clock = clock,
        )

        val result = service.generate(ReportFormat.XLSX)

        assertEquals("entrymonitor_2026-1_20260728.xlsx", result.fileName)
        assertEquals(3L, result.sizeBytes)
        assertTrue(result.downloadUrl.startsWith("/api/monitor/v11/reports/download"))
    }
}
