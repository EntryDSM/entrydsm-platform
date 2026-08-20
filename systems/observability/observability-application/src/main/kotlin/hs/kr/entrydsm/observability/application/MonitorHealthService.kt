package hs.kr.entrydsm.observability.application

import hs.kr.entrydsm.observability.application.port.`in`.GetServiceHealthUseCase
import hs.kr.entrydsm.observability.application.port.`in`.result.DependencyStatusResult
import hs.kr.entrydsm.observability.application.port.`in`.result.ServiceHealthItemResult
import hs.kr.entrydsm.observability.application.port.`in`.result.ServiceHealthResult
import hs.kr.entrydsm.observability.application.port.out.HealthCheckPort
import hs.kr.entrydsm.observability.domain.enum.ServiceName
import hs.kr.entrydsm.observability.domain.enum.ServiceStatus
import hs.kr.entrydsm.observability.domain.service.HealthStatusClassifier
import hs.kr.entrydsm.observability.domain.service.ServiceLabels
import java.time.Clock
import java.time.Instant

class MonitorHealthService(
    private val healthCheckPort: HealthCheckPort,
    private val clock: Clock,
) : GetServiceHealthUseCase {

    override fun getHealth(): ServiceHealthResult {
        // 순차 호출하면 서비스 수만큼 3초 타임아웃이 누적된다. 요청 지연을 단일 대상의 타임아웃 수준으로 묶는다.
        val services = ServiceName.entries.parallelStream().map { service ->
            val check = healthCheckPort.check(service)
            val allDependenciesUp = check.dependencies.values.all { it }
            ServiceHealthItemResult(
                service = service,
                label = ServiceLabels.of(service),
                status = HealthStatusClassifier.classify(check.responseTimeMs, allDependenciesUp),
                responseTimeMs = check.responseTimeMs,
                version = check.version,
                dependencies = check.dependencies.map { (name, up) ->
                    DependencyStatusResult(name, if (up) ServiceStatus.UP else ServiceStatus.DOWN)
                },
            )
        }.toList()
        return ServiceHealthResult(
            overall = HealthStatusClassifier.overall(services.map { it.status }),
            checkedAt = Instant.now(clock),
            services = services,
        )
    }
}
