package hs.kr.entrydsm.observability.adapterin.web.dto.response

import hs.kr.entrydsm.observability.domain.enum.MetricType
import java.time.Instant

data class MetricsSeriesResponse(
    val from: Instant,
    val to: Instant,
    val interval: String,
    val series: List<MetricSeriesResponse>,
)

data class MetricSeriesResponse(val metric: MetricType, val points: List<MetricPointResponse>)

data class MetricPointResponse(val t: Instant, val v: Long)
