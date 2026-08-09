package hs.kr.entrydsm.observability.adapterout.health

import com.fasterxml.jackson.databind.ObjectMapper
import hs.kr.entrydsm.observability.application.port.out.HealthCheckPort
import hs.kr.entrydsm.observability.application.port.out.ServiceHealthCheck
import hs.kr.entrydsm.observability.domain.enum.ServiceName
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import org.springframework.stereotype.Component

/**
 * 각 서비스의 actuator 헬스체크 엔드포인트를 호출해 상태를 판정한다.
 * ponytail: JDK 내장 HttpClient만 사용, adapter-out에 spring-web 의존성을 새로 추가하지 않는다.
 */
@Component
class ActuatorHealthCheckAdapter(
    private val properties: MonitorServiceProperties,
) : HealthCheckPort {
    private val client: HttpClient = HttpClient.newBuilder().connectTimeout(TIMEOUT).build()
    private val objectMapper = ObjectMapper()

    override fun check(service: ServiceName): ServiceHealthCheck {
        val baseUrl = properties.services[service.name.lowercase()]?.baseUrl
            ?: return ServiceHealthCheck(responseTimeMs = null, version = null, dependencies = emptyMap())

        val request = HttpRequest.newBuilder(URI.create("$baseUrl/actuator/health"))
            .timeout(TIMEOUT)
            .GET()
            .build()
        val start = System.nanoTime()
        return try {
            val response = client.send(request, HttpResponse.BodyHandlers.ofString())
            val elapsedMs = (System.nanoTime() - start) / 1_000_000
            if (response.statusCode() !in 200..299) {
                return ServiceHealthCheck(responseTimeMs = null, version = null, dependencies = emptyMap())
            }
            val body = objectMapper.readTree(response.body())
            val dependencies = body["components"]?.fields()?.asSequence()
                ?.associate { it.key to (it.value["status"]?.asText() == "UP") }
                ?: emptyMap()
            ServiceHealthCheck(responseTimeMs = elapsedMs, version = null, dependencies = dependencies)
        } catch (e: Exception) {
            ServiceHealthCheck(responseTimeMs = null, version = null, dependencies = emptyMap())
        }
    }

    companion object {
        private val TIMEOUT: Duration = Duration.ofSeconds(3)
    }
}
