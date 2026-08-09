package hs.kr.entrydsm.observability.adapterout.health

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

/** monitor.services.<lowercase 서비스명>.base-url 로 헬스체크 대상 서비스를 설정한다. */
@Component
@ConfigurationProperties(prefix = "monitor")
data class MonitorServiceProperties(
    val services: Map<String, ServiceEndpoint> = emptyMap(),
) {
    data class ServiceEndpoint(val baseUrl: String)
}
