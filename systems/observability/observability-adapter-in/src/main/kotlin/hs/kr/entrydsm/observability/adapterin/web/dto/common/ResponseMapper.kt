package hs.kr.entrydsm.observability.adapterin.web.dto.common

import hs.kr.entrydsm.observability.adapterin.web.dto.response.ApiStatsResponse
import hs.kr.entrydsm.observability.adapterin.web.dto.response.BusinessStatsResponse
import hs.kr.entrydsm.observability.adapterin.web.dto.response.ClientLogCountResponse
import hs.kr.entrydsm.observability.adapterin.web.dto.response.ConcurrentResponse
import hs.kr.entrydsm.observability.adapterin.web.dto.response.DashboardSnapshotResponse
import hs.kr.entrydsm.observability.adapterin.web.dto.response.DependencyStatusResponse
import hs.kr.entrydsm.observability.adapterin.web.dto.response.DeviceStatResponse
import hs.kr.entrydsm.observability.adapterin.web.dto.response.OutcomeCountResponse
import hs.kr.entrydsm.observability.adapterin.web.dto.response.PeriodResponse
import hs.kr.entrydsm.observability.adapterin.web.dto.response.ResourceUsageBriefResponse
import hs.kr.entrydsm.observability.adapterin.web.dto.response.ServiceActivityItemResponse
import hs.kr.entrydsm.observability.adapterin.web.dto.response.ServiceActivityResponse
import hs.kr.entrydsm.observability.adapterin.web.dto.response.ServiceHealthItemResponse
import hs.kr.entrydsm.observability.adapterin.web.dto.response.ServiceHealthResponse
import hs.kr.entrydsm.observability.adapterin.web.dto.response.SessionEventResponse
import hs.kr.entrydsm.observability.adapterin.web.dto.response.TrafficResponse
import hs.kr.entrydsm.observability.application.port.`in`.result.ApiStatsResult
import hs.kr.entrydsm.observability.application.port.`in`.result.BusinessStatsResult
import hs.kr.entrydsm.observability.application.port.`in`.result.ClientLogCountResult
import hs.kr.entrydsm.observability.application.port.`in`.result.ConcurrentResult
import hs.kr.entrydsm.observability.application.port.`in`.result.DashboardSnapshotResult
import hs.kr.entrydsm.observability.application.port.`in`.result.DependencyStatusResult
import hs.kr.entrydsm.observability.application.port.`in`.result.DeviceStatResult
import hs.kr.entrydsm.observability.application.port.`in`.result.OutcomeCountResult
import hs.kr.entrydsm.observability.application.port.`in`.result.PeriodResult
import hs.kr.entrydsm.observability.application.port.`in`.result.ResourceUsageBriefResult
import hs.kr.entrydsm.observability.application.port.`in`.result.ServiceActivityItemResult
import hs.kr.entrydsm.observability.application.port.`in`.result.ServiceActivityResult
import hs.kr.entrydsm.observability.application.port.`in`.result.ServiceHealthItemResult
import hs.kr.entrydsm.observability.application.port.`in`.result.ServiceHealthResult
import hs.kr.entrydsm.observability.application.port.`in`.result.SessionEventResult
import hs.kr.entrydsm.observability.application.port.`in`.result.TrafficResult

fun SessionEventResult.toResponse(): SessionEventResponse =
    SessionEventResponse(sessionId = sessionId, heartbeatIntervalSeconds = heartbeatIntervalSeconds)

fun ServiceHealthResult.toResponse(): ServiceHealthResponse =
    ServiceHealthResponse(
        overall = overall,
        checkedAt = checkedAt,
        services = services.map { it.toResponse() },
    )

fun ServiceHealthItemResult.toResponse(): ServiceHealthItemResponse =
    ServiceHealthItemResponse(
        service = service,
        label = label,
        status = status,
        responseTimeMs = responseTimeMs,
        version = version,
        dependencies = dependencies.map { it.toResponse() },
    )

fun DependencyStatusResult.toResponse(): DependencyStatusResponse =
    DependencyStatusResponse(name = name, status = status)

fun DashboardSnapshotResult.toResponse(): DashboardSnapshotResponse =
    DashboardSnapshotResponse(
        generatedAt = generatedAt,
        period = period.toResponse(),
        traffic = traffic.toResponse(),
        api = api.toResponse(),
        business = business.toResponse(),
        services = services.toResponse(),
        clientLog = clientLog.toResponse(),
        resource = resource.toResponse(),
    )

fun PeriodResult.toResponse(): PeriodResponse = PeriodResponse(type = type, round = round, from = from, to = to)

fun TrafficResult.toResponse(): TrafficResponse =
    TrafficResponse(
        totalVisitors = totalVisitors,
        concurrent = concurrent.toResponse(),
        avgSessionDurationSeconds = avgSessionDurationSeconds,
        devices = devices.map { it.toResponse() },
    )

fun ConcurrentResult.toResponse(): ConcurrentResponse = ConcurrentResponse(current = current, max = max, avg = avg)

fun DeviceStatResult.toResponse(): DeviceStatResponse = DeviceStatResponse(type = type, count = count, ratio = ratio)

fun ApiStatsResult.toResponse(): ApiStatsResponse =
    ApiStatsResponse(
        totalRequests = totalRequests,
        successCount = successCount,
        failureCount = failureCount,
        failureRate = failureRate,
    )

fun BusinessStatsResult.toResponse(): BusinessStatsResponse =
    BusinessStatsResponse(applicationSubmit = applicationSubmit.toResponse(), pdfDownload = pdfDownload.toResponse())

fun OutcomeCountResult.toResponse(): OutcomeCountResponse = OutcomeCountResponse(success = success, failure = failure)

fun ServiceActivityResult.toResponse(): ServiceActivityResponse =
    ServiceActivityResponse(windowSeconds = windowSeconds, items = items.map { it.toResponse() })

fun ServiceActivityItemResult.toResponse(): ServiceActivityItemResponse =
    ServiceActivityItemResponse(service = service, label = label, activeUsers = activeUsers, status = status)

fun ClientLogCountResult.toResponse(): ClientLogCountResponse =
    ClientLogCountResponse(errorCount = errorCount, warnCount = warnCount)

fun ResourceUsageBriefResult.toResponse(): ResourceUsageBriefResponse =
    ResourceUsageBriefResponse(dbUsedBytes = dbUsedBytes, bucketUsedBytes = bucketUsedBytes, measuredAt = measuredAt)
