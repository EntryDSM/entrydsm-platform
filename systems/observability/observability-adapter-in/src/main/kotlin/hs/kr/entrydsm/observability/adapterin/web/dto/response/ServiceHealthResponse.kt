package hs.kr.entrydsm.observability.adapterin.web.dto.response

import hs.kr.entrydsm.observability.domain.enum.ServiceName
import hs.kr.entrydsm.observability.domain.enum.ServiceStatus
import java.time.Instant

data class ServiceHealthResponse(
    val overall: ServiceStatus,
    val checkedAt: Instant,
    val services: List<ServiceHealthItemResponse>,
)

data class ServiceHealthItemResponse(
    val service: ServiceName,
    val label: String,
    val status: ServiceStatus,
    val responseTimeMs: Long?,
    val version: String?,
    val dependencies: List<DependencyStatusResponse>,
)

data class DependencyStatusResponse(
    val name: String,
    val status: ServiceStatus,
)
