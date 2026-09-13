package hs.kr.entrydsm.observability.application.port.out

import hs.kr.entrydsm.observability.application.port.`in`.result.DashboardSnapshotResult
import hs.kr.entrydsm.observability.domain.enum.ReportFormat

fun interface ReportGeneratorPort {
    fun generate(format: ReportFormat, snapshot: DashboardSnapshotResult): ByteArray
}
