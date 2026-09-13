package hs.kr.entrydsm.observability.application.port.`in`

import hs.kr.entrydsm.observability.application.port.`in`.result.ReportResult
import hs.kr.entrydsm.observability.domain.enum.ReportFormat

interface GenerateReportUseCase {
    fun generate(format: ReportFormat): ReportResult
}
