package hs.kr.entrydsm.observability.adapterin.web.controller

import hs.kr.entrydsm.observability.adapterin.web.dto.common.ApiResponse
import hs.kr.entrydsm.observability.adapterin.web.dto.common.toResponse
import hs.kr.entrydsm.observability.adapterin.web.dto.response.ReportGeneratedResponse
import hs.kr.entrydsm.observability.application.port.`in`.GenerateReportUseCase
import hs.kr.entrydsm.observability.domain.enum.ErrorCode
import hs.kr.entrydsm.observability.domain.enum.ReportFormat
import hs.kr.entrydsm.observability.domain.exception.MonitorDomainException
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
class ReportController(
    private val generateReportUseCase: GenerateReportUseCase,
) {
    // 파일과 다운로드 토큰을 만드는 요청이라 GET이 아니다. 프리페치나 재시도가 리포트를 새로 만들지 않게 한다.
    @PostMapping("/api/monitor/v11/reports")
    fun generate(
        @RequestParam(defaultValue = "xlsx") format: String,
    ): ApiResponse<ReportGeneratedResponse> {
        val parsedFormat = runCatching { ReportFormat.valueOf(format.trim().uppercase()) }
            .getOrElse { throw MonitorDomainException(ErrorCode.INVALID_FORMAT) }
        val result = generateReportUseCase.generate(parsedFormat)
        return ApiResponse(data = result.toResponse())
    }
}
