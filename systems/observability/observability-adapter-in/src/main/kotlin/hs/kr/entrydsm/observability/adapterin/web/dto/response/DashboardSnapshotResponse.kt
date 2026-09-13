package hs.kr.entrydsm.observability.adapterin.web.dto.response

import hs.kr.entrydsm.observability.domain.enum.DeviceType
import hs.kr.entrydsm.observability.domain.enum.ServiceStatus
import java.time.Instant

data class DashboardSnapshotResponse(
    val generatedAt: Instant,
    val period: PeriodResponse,
    val traffic: TrafficResponse,
    val api: ApiStatsResponse,
    val business: BusinessStatsResponse,
    val services: ServiceActivityResponse,
    val clientLog: ClientLogCountResponse,
    val resource: ResourceUsageBriefResponse,
)

data class PeriodResponse(val type: String, val round: String, val from: Instant, val to: Instant)

data class TrafficResponse(
    val totalVisitors: Long,
    val concurrent: ConcurrentResponse,
    val avgSessionDurationSeconds: Long,
    val devices: List<DeviceStatResponse>,
)

data class ConcurrentResponse(val current: Int, val max: Int, val avg: Int)

data class DeviceStatResponse(val type: DeviceType, val count: Long, val ratio: Double)

data class ApiStatsResponse(
    val totalRequests: Long,
    val successCount: Long,
    val failureCount: Long,
    val failureRate: Double,
)

data class BusinessStatsResponse(
    val applicationSubmit: OutcomeCountResponse,
    val pdfDownload: OutcomeCountResponse,
)

data class OutcomeCountResponse(val success: Long, val failure: Long)

data class ServiceActivityResponse(val windowSeconds: Long, val items: List<ServiceActivityItemResponse>)

data class ServiceActivityItemResponse(
    val service: String,
    val label: String,
    val activeUsers: Int,
    val status: ServiceStatus,
)

data class ClientLogCountResponse(val errorCount: Long, val warnCount: Long)

data class ResourceUsageBriefResponse(
    val dbUsedBytes: Long,
    val bucketUsedBytes: Long,
    val measuredAt: Instant,
)
