package hs.kr.entrydsm.observability.application.port.out

import hs.kr.entrydsm.observability.domain.enum.ServiceName

fun interface HealthCheckPort {
    fun check(service: ServiceName): ServiceHealthCheck
}

data class ServiceHealthCheck(
    val responseTimeMs: Long?,
    val version: String?,
    val dependencies: Map<String, Boolean>,
)
