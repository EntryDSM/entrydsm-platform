package hs.kr.entrydsm.observability.application.port.`in`.result

import hs.kr.entrydsm.observability.domain.enum.DeviceType
import hs.kr.entrydsm.observability.domain.enum.ServiceStatus
import java.time.Instant

data class DashboardSnapshotResult(
    val generatedAt: Instant,
    val period: PeriodResult,
    val traffic: TrafficResult,
    val api: ApiStatsResult,
    val business: BusinessStatsResult,
    val services: ServiceActivityResult,
    val clientLog: ClientLogCountResult,
    val resource: ResourceUsageBriefResult,
)

data class PeriodResult(
    val type: String,
    val round: String,
    val from: Instant,
    val to: Instant,
)

data class TrafficResult(
    val totalVisitors: Long,
    val concurrent: ConcurrentResult,
    val avgSessionDurationSeconds: Long,
    val devices: List<DeviceStatResult>,
)

data class ConcurrentResult(val current: Int, val max: Int, val avg: Int)

data class DeviceStatResult(val type: DeviceType, val count: Long, val ratio: Double)

/** 다른 서비스로부터 API 요청 지표를 받는 수집 경로가 아직 없어 항상 0으로 반환된다. */
data class ApiStatsResult(
    val totalRequests: Long,
    val successCount: Long,
    val failureCount: Long,
    val failureRate: Double,
)

/** 원서접수/PDF다운로드 도메인 이벤트를 받는 수집 경로가 아직 없어 항상 0으로 반환된다. */
data class BusinessStatsResult(
    val applicationSubmit: OutcomeCountResult,
    val pdfDownload: OutcomeCountResult,
)

data class OutcomeCountResult(val success: Long, val failure: Long)

data class ServiceActivityResult(val windowSeconds: Long, val items: List<ServiceActivityItemResult>)

data class ServiceActivityItemResult(
    val service: String,
    val label: String,
    val activeUsers: Int,
    val status: ServiceStatus,
)

data class ClientLogCountResult(val errorCount: Long, val warnCount: Long)

data class ResourceUsageBriefResult(
    val dbUsedBytes: Long,
    val bucketUsedBytes: Long,
    val measuredAt: Instant,
)
