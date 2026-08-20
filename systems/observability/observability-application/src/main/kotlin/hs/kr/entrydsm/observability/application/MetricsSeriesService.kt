package hs.kr.entrydsm.observability.application

import hs.kr.entrydsm.observability.application.port.`in`.GetMetricsSeriesUseCase
import hs.kr.entrydsm.observability.application.port.`in`.result.MetricPointResult
import hs.kr.entrydsm.observability.application.port.`in`.result.MetricSeriesResult
import hs.kr.entrydsm.observability.application.port.`in`.result.MetricsSeriesResult
import hs.kr.entrydsm.observability.application.port.out.MetricsStorePort
import hs.kr.entrydsm.observability.domain.enum.ErrorCode
import hs.kr.entrydsm.observability.domain.enum.MetricType
import hs.kr.entrydsm.observability.domain.exception.MonitorDomainException
import hs.kr.entrydsm.observability.domain.service.TimeBucketer
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

class MetricsSeriesService(
    private val metricsStorePort: MetricsStorePort,
    private val clock: Clock,
) : GetMetricsSeriesUseCase {

    // ponytail: 버킷마다 저장소를 한 번씩 조회한다(최대 MAX_BUCKETS회).
    // 관리자 조회용이라 감내 가능, 느려지면 MetricsStorePort에 구간 일괄 조회를 추가해 파이프라인으로 묶는다.
    override fun getSeries(metrics: List<MetricType>, from: Instant?, to: Instant?, interval: String?): MetricsSeriesResult {
        if (metrics.isEmpty()) throw MonitorDomainException(ErrorCode.INVALID_METRIC)

        val now = Instant.now(clock)
        val resolvedTo = to ?: now
        val resolvedFrom = from ?: now.atZone(ZONE).toLocalDate().atStartOfDay(ZONE).toInstant()
        if (resolvedFrom.isAfter(resolvedTo) || Duration.between(resolvedFrom, resolvedTo) > MAX_RANGE) {
            throw MonitorDomainException(ErrorCode.INVALID_TIME_RANGE)
        }

        val resolvedInterval = interval ?: "1h"
        val duration = TimeBucketer.durationOf(resolvedInterval) ?: throw MonitorDomainException(ErrorCode.INVALID_INTERVAL)
        // 리스트를 만든 뒤 세면 90일 x 5m 같은 요청에서 2만 개가 넘는 Instant를 만들고 바로 버린다.
        if (TimeBucketer.bucketCount(resolvedFrom, resolvedTo, duration) > MAX_BUCKETS) {
            throw MonitorDomainException(ErrorCode.INVALID_INTERVAL)
        }
        val buckets = TimeBucketer.bucketStarts(resolvedFrom, resolvedTo, duration)

        val series = metrics.map { metric ->
            val points = buckets.map { bucketStart ->
                val value = when (metric) {
                    MetricType.VISITOR -> metricsStorePort.visitorCount(bucketStart, bucketStart.plus(duration))
                    // 다른 서비스로부터 API 요청 지표를 받는 수집 경로가 아직 없어 0으로 고정한다.
                    MetricType.API_REQUEST -> 0L
                }
                MetricPointResult(bucketStart, value)
            }
            MetricSeriesResult(metric, points)
        }
        return MetricsSeriesResult(resolvedFrom, resolvedTo, resolvedInterval, series)
    }

    companion object {
        private val ZONE: ZoneId = ZoneId.of("Asia/Seoul")
        private val MAX_RANGE: Duration = Duration.ofDays(90)
        private const val MAX_BUCKETS = 1000
    }
}
