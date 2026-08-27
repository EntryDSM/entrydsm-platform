package hs.kr.entrydsm.observability.adapterin.web.dto.common

import hs.kr.entrydsm.observability.adapterin.web.dto.response.ApiStatsResponse
import hs.kr.entrydsm.observability.adapterin.web.dto.response.BusinessStatsResponse
import hs.kr.entrydsm.observability.adapterin.web.dto.response.ClientLogAcceptResponse
import hs.kr.entrydsm.observability.adapterin.web.dto.response.ClientLogCountResponse
import hs.kr.entrydsm.observability.adapterin.web.dto.response.ClientLogItemResponse
import hs.kr.entrydsm.observability.adapterin.web.dto.response.ClientLogPageResponse
import hs.kr.entrydsm.observability.adapterin.web.dto.response.ConcurrentResponse
import hs.kr.entrydsm.observability.adapterin.web.dto.response.DashboardSnapshotResponse
import hs.kr.entrydsm.observability.adapterin.web.dto.response.DependencyStatusResponse
import hs.kr.entrydsm.observability.adapterin.web.dto.response.DeviceStatResponse
import hs.kr.entrydsm.observability.adapterin.web.dto.response.MetricPointResponse
import hs.kr.entrydsm.observability.adapterin.web.dto.response.MetricSeriesResponse
import hs.kr.entrydsm.observability.adapterin.web.dto.response.MetricsSeriesResponse
import hs.kr.entrydsm.observability.adapterin.web.dto.response.OutcomeCountResponse
import hs.kr.entrydsm.observability.adapterin.web.dto.response.PeriodResponse
import hs.kr.entrydsm.observability.adapterin.web.dto.response.ResourceUsageBriefResponse
import hs.kr.entrydsm.observability.adapterin.web.dto.response.ServerLogItemResponse
import hs.kr.entrydsm.observability.adapterin.web.dto.response.ServerLogPageResponse
import hs.kr.entrydsm.observability.adapterin.web.dto.response.ServiceActivityItemResponse
import hs.kr.entrydsm.observability.adapterin.web.dto.response.ServiceActivityResponse
import hs.kr.entrydsm.observability.adapterin.web.dto.response.ReportGeneratedResponse
import hs.kr.entrydsm.observability.adapterin.web.dto.response.ServiceHealthItemResponse
import hs.kr.entrydsm.observability.adapterin.web.dto.response.ServiceHealthResponse
import hs.kr.entrydsm.observability.adapterin.web.dto.response.SessionEventResponse
import hs.kr.entrydsm.observability.adapterin.web.dto.response.BucketUsageResponse
import hs.kr.entrydsm.observability.adapterin.web.dto.response.DatabaseUsageResponse
import hs.kr.entrydsm.observability.adapterin.web.dto.response.StorageUsageResponse
import hs.kr.entrydsm.observability.adapterin.web.dto.response.TrafficResponse
import hs.kr.entrydsm.observability.application.port.`in`.result.ApiStatsResult
import hs.kr.entrydsm.observability.application.port.`in`.result.BusinessStatsResult
import hs.kr.entrydsm.observability.application.port.`in`.result.ClientLogAcceptResult
import hs.kr.entrydsm.observability.application.port.`in`.result.ClientLogCountResult
import hs.kr.entrydsm.observability.application.port.out.ClientLogEntry
import hs.kr.entrydsm.observability.application.port.out.ClientLogPage
import hs.kr.entrydsm.observability.application.port.out.ServerLogEntry
import hs.kr.entrydsm.observability.application.port.out.ServerLogPage
import hs.kr.entrydsm.observability.application.port.`in`.result.ConcurrentResult
import hs.kr.entrydsm.observability.application.port.`in`.result.DashboardSnapshotResult
import hs.kr.entrydsm.observability.application.port.`in`.result.DependencyStatusResult
import hs.kr.entrydsm.observability.application.port.`in`.result.DeviceStatResult
import hs.kr.entrydsm.observability.application.port.`in`.result.MetricPointResult
import hs.kr.entrydsm.observability.application.port.`in`.result.MetricSeriesResult
import hs.kr.entrydsm.observability.application.port.`in`.result.MetricsSeriesResult
import hs.kr.entrydsm.observability.application.port.`in`.result.OutcomeCountResult
import hs.kr.entrydsm.observability.application.port.`in`.result.PeriodResult
import hs.kr.entrydsm.observability.application.port.`in`.result.ResourceUsageBriefResult
import hs.kr.entrydsm.observability.application.port.`in`.result.ServiceActivityItemResult
import hs.kr.entrydsm.observability.application.port.`in`.result.ServiceActivityResult
import hs.kr.entrydsm.observability.application.port.`in`.result.ServiceHealthItemResult
import hs.kr.entrydsm.observability.application.port.`in`.result.ReportResult
import hs.kr.entrydsm.observability.application.port.`in`.result.ServiceHealthResult
import hs.kr.entrydsm.observability.application.port.`in`.result.SessionEventResult
import hs.kr.entrydsm.observability.application.port.`in`.result.TrafficResult
import hs.kr.entrydsm.observability.application.port.`in`.result.BucketUsageResult
import hs.kr.entrydsm.observability.application.port.`in`.result.DatabaseUsageResult
import hs.kr.entrydsm.observability.application.port.`in`.result.StorageUsageResult

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

fun MetricsSeriesResult.toResponse(): MetricsSeriesResponse =
    MetricsSeriesResponse(from = from, to = to, interval = interval, series = series.map { it.toResponse() })

fun MetricSeriesResult.toResponse(): MetricSeriesResponse =
    MetricSeriesResponse(metric = metric, points = points.map { it.toResponse() })

fun MetricPointResult.toResponse(): MetricPointResponse = MetricPointResponse(t = t, v = v)

fun ClientLogAcceptResult.toResponse(): ClientLogAcceptResponse = ClientLogAcceptResponse(accepted = accepted, rejected = rejected)

fun ClientLogPage.toResponse(): ClientLogPageResponse =
    ClientLogPageResponse(
        totalCount = totalCount,
        errorCount = errorCount,
        warnCount = warnCount,
        items = items.map { it.toResponse() },
        nextCursor = nextCursor?.encode(),
        hasNext = hasNext,
    )

fun ClientLogEntry.toResponse(): ClientLogItemResponse =
    ClientLogItemResponse(
        fingerprint = fingerprint,
        level = level,
        message = message,
        source = source,
        pageUrl = pageUrl,
        browser = browser,
        os = os,
        count = count,
        firstOccurredAt = firstOccurredAt,
        lastOccurredAt = lastOccurredAt,
    )

fun ServerLogPage.toResponse(): ServerLogPageResponse =
    ServerLogPageResponse(
        totalCount = totalCount,
        items = items.map { it.toResponse() },
        nextCursor = nextCursor?.encode(),
        hasNext = hasNext,
    )

fun ServerLogEntry.toResponse(): ServerLogItemResponse =
    ServerLogItemResponse(
        fingerprint = fingerprint,
        service = service,
        method = method,
        path = path,
        status = status,
        code = code,
        grpcStatus = grpcStatus,
        message = message,
        count = count,
        firstOccurredAt = firstOccurredAt,
        lastOccurredAt = lastOccurredAt,
    )

fun StorageUsageResult.toResponse(): StorageUsageResponse =
    StorageUsageResponse(database = database.toResponse(), bucket = bucket.toResponse(), cacheTtlSeconds = cacheTtlSeconds)

fun DatabaseUsageResult.toResponse(): DatabaseUsageResponse =
    DatabaseUsageResponse(usedBytes = usedBytes, totalBytes = totalBytes, usageRatio = usageRatio, measuredAt = measuredAt)

fun BucketUsageResult.toResponse(): BucketUsageResponse =
    BucketUsageResponse(usedBytes = usedBytes, objectCount = objectCount, measuredAt = measuredAt)

fun ReportResult.toResponse(): ReportGeneratedResponse =
    ReportGeneratedResponse(
        status = "READY",
        downloadUrl = downloadUrl,
        fileName = fileName,
        sizeBytes = sizeBytes,
        expiresAt = expiresAt,
    )
