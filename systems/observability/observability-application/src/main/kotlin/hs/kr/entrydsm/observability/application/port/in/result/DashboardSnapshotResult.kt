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

data class ApiStatsResult(
    val totalRequests: Long,
    val successCount: Long,
    val failureCount: Long,
    val failureRate: Double,
)

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
