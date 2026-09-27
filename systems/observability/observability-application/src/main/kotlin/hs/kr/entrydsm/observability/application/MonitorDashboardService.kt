package hs.kr.entrydsm.observability.application

import hs.kr.entrydsm.observability.application.port.`in`.GetDashboardSnapshotUseCase
import hs.kr.entrydsm.observability.application.port.`in`.SampleConcurrencyUseCase
import hs.kr.entrydsm.observability.application.port.`in`.result.ApiStatsResult
import hs.kr.entrydsm.observability.application.port.`in`.result.BusinessStatsResult
import hs.kr.entrydsm.observability.application.port.`in`.result.ClientLogCountResult
import hs.kr.entrydsm.observability.application.port.`in`.result.ConcurrentResult
import hs.kr.entrydsm.observability.application.port.`in`.result.DashboardSnapshotResult
import hs.kr.entrydsm.observability.application.port.`in`.result.DeviceStatResult
import hs.kr.entrydsm.observability.application.port.`in`.result.OutcomeCountResult
import hs.kr.entrydsm.observability.application.port.`in`.result.PeriodResult
import hs.kr.entrydsm.observability.application.port.`in`.result.ResourceUsageBriefResult
import hs.kr.entrydsm.observability.application.port.`in`.result.ServiceActivityItemResult
import hs.kr.entrydsm.observability.application.port.`in`.result.ServiceActivityResult
import hs.kr.entrydsm.observability.application.port.`in`.result.TrafficResult
import hs.kr.entrydsm.observability.application.port.out.ClientLogStorePort
import hs.kr.entrydsm.observability.application.port.out.HealthCheckPort
import hs.kr.entrydsm.observability.application.port.out.MetricsStorePort
import hs.kr.entrydsm.observability.application.port.out.RoundPort
import hs.kr.entrydsm.observability.application.port.out.SessionStorePort
import hs.kr.entrydsm.observability.application.port.out.StorageUsagePort
import hs.kr.entrydsm.observability.domain.enum.DeviceType
import hs.kr.entrydsm.observability.domain.enum.ErrorCode
import hs.kr.entrydsm.observability.domain.enum.LogLevel
import hs.kr.entrydsm.observability.domain.enum.ServiceName
import hs.kr.entrydsm.observability.domain.exception.MonitorDomainException
import hs.kr.entrydsm.observability.domain.service.HealthStatusClassifier
import hs.kr.entrydsm.observability.domain.service.ServiceLabels
import java.time.Clock
import java.time.Duration
import java.time.Instant

class MonitorDashboardService(
    private val sessionStorePort: SessionStorePort,
    private val healthCheckPort: HealthCheckPort,
    private val clientLogStorePort: ClientLogStorePort,
    private val storageUsagePort: StorageUsagePort,
    private val roundPort: RoundPort,
    private val metricsStorePort: MetricsStorePort,
    private val clock: Clock,
) : GetDashboardSnapshotUseCase, SampleConcurrencyUseCase {

    override fun getSnapshot(round: String?): DashboardSnapshotResult {
        val currentRound = roundPort.current()
        if (round != null && !round.equals(currentRound.name, ignoreCase = true)) {
            throw MonitorDomainException(ErrorCode.ROUND_NOT_FOUND)
        }
        val now = Instant.now(clock)

        val totalVisitors = sessionStorePort.totalVisitors()
        val deviceCounts = sessionStorePort.deviceBreakdown()
        val devices = DeviceType.entries.map { type ->
            val count = deviceCounts[type] ?: 0L
            DeviceStatResult(type, count, ratio(count, totalVisitors))
        }

        // 헬스체크는 외부 HTTP 호출이라 순차 실행하면 서비스 수만큼 타임아웃이 누적된다.
        val perServiceItems = ServiceName.entries.parallelStream().map { service ->
            val check = healthCheckPort.check(service)
            val status = HealthStatusClassifier.classify(check.responseTimeMs, check.dependencies.values.all { it })
            ServiceActivityItemResult(
                service = service.name,
                label = ServiceLabels.of(service),
                activeUsers = sessionStorePort.concurrentUsers(service, now, WINDOW_SECONDS),
                status = status,
            )
        }.toList()
        val totalActiveUsers = sessionStorePort.concurrentUsers(null, now, WINDOW_SECONDS)
        val totalItem = ServiceActivityItemResult(
            service = "TOTAL",
            label = "종합",
            activeUsers = totalActiveUsers,
            status = HealthStatusClassifier.overall(perServiceItems.map { it.status }),
        )

        val logCounts = clientLogStorePort.countByLevel(now.minus(CLIENT_LOG_WINDOW), now)
        val storage = storageUsagePort.measure()
        val apiSuccess = metricsStorePort.apiRequestCount(currentRound.from, now, true)
        val apiFailure = metricsStorePort.apiRequestCount(currentRound.from, now, false)
        val apiTotal = apiSuccess + apiFailure

        return DashboardSnapshotResult(
            generatedAt = now,
            period = PeriodResult(
                type = "ADMISSION",
                round = currentRound.name,
                from = currentRound.from,
                to = currentRound.to,
            ),
            traffic = TrafficResult(
                totalVisitors = totalVisitors,
                concurrent = ConcurrentResult(
                    current = totalActiveUsers,
                    max = sessionStorePort.concurrentMax(),
                    avg = sessionStorePort.concurrentAvg(),
                ),
                avgSessionDurationSeconds = sessionStorePort.avgSessionDurationSeconds(),
                devices = devices,
            ),
            api = ApiStatsResult(apiTotal, apiSuccess, apiFailure, ratio(apiFailure, apiTotal)),
            business = BusinessStatsResult(
                applicationSubmit = outcome("application-submit", currentRound.from, now),
                pdfDownload = outcome("pdf-download", currentRound.from, now),
            ),
            services = ServiceActivityResult(windowSeconds = WINDOW_SECONDS, items = listOf(totalItem) + perServiceItems),
            clientLog = ClientLogCountResult(
                errorCount = logCounts[LogLevel.ERROR] ?: 0,
                warnCount = logCounts[LogLevel.WARN] ?: 0,
            ),
            resource = ResourceUsageBriefResult(
                dbUsedBytes = storage.databaseUsedBytes,
                bucketUsedBytes = storage.bucketUsedBytes,
                measuredAt = storage.measuredAt,
            ),
        )
    }

    /** 대시보드의 현재 동시접속과 같은 창으로 재야 최대·평균과 비교할 수 있다. */
    override fun sampleConcurrency() {
        sessionStorePort.sampleConcurrency(Instant.now(clock), WINDOW_SECONDS)
    }

    private fun ratio(count: Long, total: Long): Double = if (total == 0L) 0.0 else count.toDouble() / total

    private fun outcome(type: String, from: Instant, to: Instant) = OutcomeCountResult(
        success = metricsStorePort.businessCount(type, from, to, true),
        failure = metricsStorePort.businessCount(type, from, to, false),
    )

    companion object {
        private const val WINDOW_SECONDS = 30L
        private val CLIENT_LOG_WINDOW: Duration = Duration.ofHours(1)
    }
}
