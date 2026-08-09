package hs.kr.entrydsm.observability.application

import hs.kr.entrydsm.observability.application.port.`in`.GetDashboardSnapshotUseCase
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
import org.springframework.stereotype.Service

@Service
class MonitorDashboardService(
    private val sessionStorePort: SessionStorePort,
    private val healthCheckPort: HealthCheckPort,
    private val clientLogStorePort: ClientLogStorePort,
    private val storageUsagePort: StorageUsagePort,
    private val roundPort: RoundPort,
    private val clock: Clock,
) : GetDashboardSnapshotUseCase {

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

        val perServiceItems = ServiceName.entries.map { service ->
            val check = healthCheckPort.check(service)
            val status = HealthStatusClassifier.classify(check.responseTimeMs, check.dependencies.values.all { it })
            ServiceActivityItemResult(
                service = service.name,
                label = ServiceLabels.of(service),
                activeUsers = sessionStorePort.concurrentUsers(service, now, WINDOW_SECONDS),
                status = status,
            )
        }
        val totalActiveUsers = sessionStorePort.concurrentUsers(null, now, WINDOW_SECONDS)
        val totalItem = ServiceActivityItemResult(
            service = "TOTAL",
            label = "종합",
            activeUsers = totalActiveUsers,
            status = HealthStatusClassifier.overall(perServiceItems.map { it.status }),
        )

        val logCounts = clientLogStorePort.countByLevel(now.minus(CLIENT_LOG_WINDOW), now)
        val storage = storageUsagePort.measure()

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
            // 다른 서비스의 API 요청 지표를 받는 수집 경로가 아직 없어 0으로 고정한다.
            api = ApiStatsResult(totalRequests = 0, successCount = 0, failureCount = 0, failureRate = 0.0),
            // 원서접수/PDF다운로드 도메인 이벤트를 받는 수집 경로가 아직 없어 0으로 고정한다.
            business = BusinessStatsResult(
                applicationSubmit = OutcomeCountResult(0, 0),
                pdfDownload = OutcomeCountResult(0, 0),
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

    private fun ratio(count: Long, total: Long): Double = if (total == 0L) 0.0 else count.toDouble() / total

    companion object {
        private const val WINDOW_SECONDS = 30L
        private val CLIENT_LOG_WINDOW: Duration = Duration.ofHours(1)
    }
}
