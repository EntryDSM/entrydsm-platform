package hs.kr.entrydsm.observability.adapterin.web.controller

import hs.kr.entrydsm.observability.adapterin.web.dto.common.ApiResponse
import hs.kr.entrydsm.observability.adapterin.web.dto.common.toResponse
import hs.kr.entrydsm.observability.adapterin.web.dto.response.MetricsSeriesResponse
import hs.kr.entrydsm.observability.application.port.`in`.GetMetricsSeriesUseCase
import hs.kr.entrydsm.observability.domain.enum.ErrorCode
import hs.kr.entrydsm.observability.domain.enum.MetricType
import hs.kr.entrydsm.observability.domain.exception.MonitorDomainException
import java.time.Instant
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
class MetricsSeriesController(
    private val getMetricsSeriesUseCase: GetMetricsSeriesUseCase,
) {
    @GetMapping("/api/monitor/v11/metrics/series")
    fun series(
        @RequestParam metrics: String,
        @RequestParam(required = false) from: Instant?,
        @RequestParam(required = false) to: Instant?,
        @RequestParam(required = false) interval: String?,
    ): ApiResponse<MetricsSeriesResponse> {
        val parsedMetrics = metrics.split(",").map { raw ->
            runCatching { MetricType.valueOf(raw.trim()) }
                .getOrElse { throw MonitorDomainException(ErrorCode.INVALID_METRIC) }
        }
        val result = getMetricsSeriesUseCase.getSeries(parsedMetrics, from, to, interval)
        return ApiResponse(data = result.toResponse())
    }
}
