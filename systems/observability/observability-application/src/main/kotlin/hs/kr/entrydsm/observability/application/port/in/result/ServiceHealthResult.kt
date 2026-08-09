package hs.kr.entrydsm.observability.application.port.`in`.result

import hs.kr.entrydsm.observability.domain.enum.ServiceName
import hs.kr.entrydsm.observability.domain.enum.ServiceStatus
import java.time.Instant

data class ServiceHealthResult(
    val overall: ServiceStatus,
    val checkedAt: Instant,
    val services: List<ServiceHealthItemResult>,
)

data class ServiceHealthItemResult(
    val service: ServiceName,
    val label: String,
    val status: ServiceStatus,
    val responseTimeMs: Long?,
    val version: String?,
    val dependencies: List<DependencyStatusResult>,
)

data class DependencyStatusResult(
    val name: String,
    val status: ServiceStatus,
)
