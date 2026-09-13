package hs.kr.entrydsm.observability.adapterout.report

import hs.kr.entrydsm.observability.application.port.`in`.result.DashboardSnapshotResult
import hs.kr.entrydsm.observability.application.port.out.ReportGeneratorPort
import hs.kr.entrydsm.observability.domain.enum.ReportFormat
import java.io.ByteArrayOutputStream
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.springframework.stereotype.Component

@Component
class XlsxCsvReportGenerator : ReportGeneratorPort {

    override fun generate(format: ReportFormat, snapshot: DashboardSnapshotResult): ByteArray =
        when (format) {
            ReportFormat.XLSX -> toXlsx(snapshot)
            ReportFormat.CSV -> toCsv(snapshot).toByteArray()
        }

    private fun rows(snapshot: DashboardSnapshotResult): List<Pair<String, String>> =
        listOf(
            "generatedAt" to snapshot.generatedAt.toString(),
            "round" to snapshot.period.round,
            "totalVisitors" to snapshot.traffic.totalVisitors.toString(),
            "concurrentCurrent" to snapshot.traffic.concurrent.current.toString(),
            "concurrentMax" to snapshot.traffic.concurrent.max.toString(),
            "concurrentAvg" to snapshot.traffic.concurrent.avg.toString(),
            "avgSessionDurationSeconds" to snapshot.traffic.avgSessionDurationSeconds.toString(),
            "apiTotalRequests" to snapshot.api.totalRequests.toString(),
            "apiSuccessCount" to snapshot.api.successCount.toString(),
            "apiFailureCount" to snapshot.api.failureCount.toString(),
            "applicationSubmitSuccess" to snapshot.business.applicationSubmit.success.toString(),
            "applicationSubmitFailure" to snapshot.business.applicationSubmit.failure.toString(),
            "pdfDownloadSuccess" to snapshot.business.pdfDownload.success.toString(),
            "pdfDownloadFailure" to snapshot.business.pdfDownload.failure.toString(),
            "clientLogErrorCount" to snapshot.clientLog.errorCount.toString(),
            "clientLogWarnCount" to snapshot.clientLog.warnCount.toString(),
            "dbUsedBytes" to snapshot.resource.dbUsedBytes.toString(),
            "bucketUsedBytes" to snapshot.resource.bucketUsedBytes.toString(),
        ) + snapshot.services.items.map { "activeUsers_${it.service}" to it.activeUsers.toString() }

    private fun toCsv(snapshot: DashboardSnapshotResult): String =
        buildString {
            appendLine("metric,value")
            rows(snapshot).forEach { (key, value) -> appendLine("${escapeCsv(key)},${escapeCsv(value)}") }
        }

    /** RFC 4180: 쉼표·큰따옴표·줄바꿈이 들어가도 열 구조가 깨지지 않게 감싸고 내부 큰따옴표는 두 번 쓴다. */
    private fun escapeCsv(field: String): String = "\"" + field.replace("\"", "\"\"") + "\""

    private fun toXlsx(snapshot: DashboardSnapshotResult): ByteArray {
        XSSFWorkbook().use { workbook ->
            val sheet = workbook.createSheet("monitor")
            sheet.createRow(0).apply {
                createCell(0).setCellValue("metric")
                createCell(1).setCellValue("value")
            }
            rows(snapshot).forEachIndexed { index, (key, value) ->
                sheet.createRow(index + 1).apply {
                    createCell(0).setCellValue(key)
                    createCell(1).setCellValue(value)
                }
            }
            val out = ByteArrayOutputStream()
            workbook.write(out)
            return out.toByteArray()
        }
    }
}
