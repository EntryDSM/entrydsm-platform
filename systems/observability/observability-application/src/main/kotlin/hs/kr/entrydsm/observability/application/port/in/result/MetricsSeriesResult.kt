package hs.kr.entrydsm.observability.application.port.`in`.result

import hs.kr.entrydsm.observability.domain.enum.MetricType
import java.time.Instant

data class MetricsSeriesResult(
    val from: Instant,
    val to: Instant,
    val interval: String,
    val series: List<MetricSeriesResult>,
)

data class MetricSeriesResult(val metric: MetricType, val points: List<MetricPointResult>)

data class MetricPointResult(val t: Instant, val v: Long)
