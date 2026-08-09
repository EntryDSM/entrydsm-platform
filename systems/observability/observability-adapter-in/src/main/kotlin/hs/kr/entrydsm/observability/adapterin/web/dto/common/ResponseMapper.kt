package hs.kr.entrydsm.observability.adapterin.web.dto.common

import hs.kr.entrydsm.observability.adapterin.web.dto.response.DependencyStatusResponse
import hs.kr.entrydsm.observability.adapterin.web.dto.response.ServiceHealthItemResponse
import hs.kr.entrydsm.observability.adapterin.web.dto.response.ServiceHealthResponse
import hs.kr.entrydsm.observability.adapterin.web.dto.response.SessionEventResponse
import hs.kr.entrydsm.observability.application.port.`in`.result.DependencyStatusResult
import hs.kr.entrydsm.observability.application.port.`in`.result.ServiceHealthItemResult
import hs.kr.entrydsm.observability.application.port.`in`.result.ServiceHealthResult
import hs.kr.entrydsm.observability.application.port.`in`.result.SessionEventResult

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
