package hs.kr.entrydsm.observability.adapterout.health

import hs.kr.entrydsm.observability.application.port.out.HealthCheckPort
import hs.kr.entrydsm.observability.application.port.out.ServiceHealthCheck
import hs.kr.entrydsm.observability.domain.enum.ServiceName
import org.springframework.boot.health.actuate.endpoint.CompositeHealthDescriptor
import org.springframework.boot.health.actuate.endpoint.HealthEndpoint
import org.springframework.boot.health.contributor.Status
import org.springframework.stereotype.Component

/** 프로세스 상태 한 장면. */
data class ProcessHealth(
    val up: Boolean,
    val dependencies: Map<String, Boolean>,
)

fun interface ProcessHealthSource {
    fun read(): ProcessHealth
}

/** Actuator 가 모아 둔 상태를 그대로 읽는다. */
@Component
class ActuatorProcessHealthSource(
    private val healthEndpoint: HealthEndpoint,
) : ProcessHealthSource {
    override fun read(): ProcessHealth {
        val descriptor = healthEndpoint.health()
        val dependencies = (descriptor as? CompositeHealthDescriptor)
            ?.components
            ?.mapValues { (_, component) -> component.status == Status.UP }
            ?: emptyMap()
        return ProcessHealth(up = descriptor.status == Status.UP, dependencies = dependencies)
    }
}

/**
 * 프로세스 자신의 상태를 읽는다. 서비스별 actuator 를 HTTP 로 호출하던 어댑터를 대신한다.
 *
 * 논리 서비스(IDENTITY, APPLICATION ...)가 모두 한 프로세스에 있으므로 어느 이름으로 물어도 같은 상태다.
 * 자기 자신을 HTTP 로 다시 부르면 응답 시간도 상태도 의미가 없어 in-process 로 읽는다.
 */
@Component
class InProcessHealthCheckAdapter(
    private val processHealth: ProcessHealthSource,
) : HealthCheckPort {
    override fun check(service: ServiceName): ServiceHealthCheck {
        val start = System.nanoTime()
        val health = processHealth.read()
        val elapsedMs = (System.nanoTime() - start) / NANOS_PER_MILLI

        // 상태가 UP 이 아니면 응답 시간을 비운다. 판정 규칙은 HealthStatusClassifier 가 그대로 쓴다.
        return ServiceHealthCheck(
            responseTimeMs = elapsedMs.takeIf { health.up },
            version = null,
            dependencies = health.dependencies,
        )
    }

    private companion object {
        const val NANOS_PER_MILLI = 1_000_000
    }
}
