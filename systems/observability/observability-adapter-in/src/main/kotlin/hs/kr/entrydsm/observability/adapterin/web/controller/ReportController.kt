package hs.kr.entrydsm.observability.adapterin.web.controller

import hs.kr.entrydsm.observability.adapterin.web.dto.common.ApiResponse
import hs.kr.entrydsm.observability.adapterin.web.dto.common.toResponse
import hs.kr.entrydsm.observability.adapterin.web.dto.response.ReportGeneratedResponse
import hs.kr.entrydsm.observability.application.port.`in`.GenerateReportUseCase
import hs.kr.entrydsm.observability.domain.enum.ErrorCode
import hs.kr.entrydsm.observability.domain.enum.ReportFormat
import hs.kr.entrydsm.observability.domain.exception.MonitorDomainException
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
class ReportController(
    private val generateReportUseCase: GenerateReportUseCase,
) {
    @GetMapping("/api/monitor/v11/reports")
    fun generate(
        @RequestParam(defaultValue = "xlsx") format: String,
    ): ApiResponse<ReportGeneratedResponse> {
        val parsedFormat = runCatching { ReportFormat.valueOf(format.trim().uppercase()) }
            .getOrElse { throw MonitorDomainException(ErrorCode.INVALID_FORMAT) }
        val result = generateReportUseCase.generate(parsedFormat)
        return ApiResponse(data = result.toResponse())
    }
}
