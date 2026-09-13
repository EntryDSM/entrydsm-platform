package hs.kr.entrydsm.observability.application.port.`in`

import hs.kr.entrydsm.observability.application.port.`in`.result.MetricsSeriesResult
import hs.kr.entrydsm.observability.domain.enum.MetricType
import java.time.Instant

interface GetMetricsSeriesUseCase {
    fun getSeries(metrics: List<MetricType>, from: Instant?, to: Instant?, interval: String?): MetricsSeriesResult
}
