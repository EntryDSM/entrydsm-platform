package hs.kr.entrydsm.identity.config.edge

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 엣지 설정. gateway 의 `spring.cloud.gateway...globalcors` 와 `gateway.request.max-body-bytes` 를 옮겼다.
 * 라우팅·재시도·서킷브레이커 설정은 프로세스가 하나라 사라진다.
 */
@ConfigurationProperties(prefix = "edge")
data class EdgeProperties(
    val cors: Cors = Cors(),
    val request: Request = Request(),
) {
    data class Cors(
        val allowedOrigins: List<String> = emptyList(),
        val allowedMethods: List<String> = listOf("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"),
        val allowedHeaders: List<String> = listOf("Authorization", "Content-Type", "X-Trace-Id", "X-XSRF-TOKEN"),
        val exposedHeaders: List<String> = listOf("X-Trace-Id"),
        val allowCredentials: Boolean = true,
        val maxAgeSeconds: Long = 3600,
    )

    data class Request(
        /** 본문 크기 상한. gateway 기본값과 같은 10 MiB. */
        val maxBodyBytes: Long = 10 * 1024 * 1024,
    )
}
