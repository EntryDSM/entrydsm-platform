package hs.kr.entrydsm.observability.adapterout.report

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
import hs.kr.entrydsm.observability.domain.enum.ReportFormat
import java.io.ByteArrayInputStream
import java.time.Instant
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class XlsxCsvReportGeneratorTest {
    private val generator = XlsxCsvReportGenerator()

    private fun snapshot(round: String) = DashboardSnapshotResult(
        generatedAt = Instant.parse("2026-07-28T14:03:11Z"),
        period = PeriodResult("ADMISSION", round, Instant.EPOCH, Instant.EPOCH),
        traffic = TrafficResult(7, ConcurrentResult(1, 2, 3), 4, emptyList()),
        api = ApiStatsResult(0, 0, 0, 0.0),
        business = BusinessStatsResult(OutcomeCountResult(0, 0), OutcomeCountResult(0, 0)),
        services = ServiceActivityResult(30, emptyList()),
        clientLog = ClientLogCountResult(0, 0),
        resource = ResourceUsageBriefResult(0, 0, Instant.parse("2026-07-28T14:03:11Z")),
    )

    private fun roundLine(csv: String): String = csv.lineSequence().first { it.startsWith("\"round\"") }

    @Test
    fun csvQuotesEveryFieldAndDoublesInnerQuotes() {
        val csv = String(generator.generate(ReportFormat.CSV, snapshot("""2026-1 "특별"""")))

        assertEquals("metric,value", csv.lineSequence().first())
        assertEquals(""""round","2026-1 ""특별"""""", roundLine(csv))
    }

    @Test
    fun csvKeepsOneValueColumnWhenValueContainsComma() {
        val csv = String(generator.generate(ReportFormat.CSV, snapshot("a,b")))

        assertEquals(""""round","a,b"""", roundLine(csv))
    }

    @Test
    fun xlsxWritesMetricValueRows() {
        val bytes = generator.generate(ReportFormat.XLSX, snapshot("2026-1"))

        XSSFWorkbook(ByteArrayInputStream(bytes)).use { workbook ->
            val sheet = workbook.getSheet("monitor")
            assertEquals("metric", sheet.getRow(0).getCell(0).stringCellValue)
            assertEquals("value", sheet.getRow(0).getCell(1).stringCellValue)
            assertEquals("generatedAt", sheet.getRow(1).getCell(0).stringCellValue)
            assertEquals("2026-1", sheet.getRow(2).getCell(1).stringCellValue)
            assertTrue(sheet.getRow(3).getCell(1).stringCellValue == "7")
        }
    }
}
