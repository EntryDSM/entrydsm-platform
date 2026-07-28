package hs.kr.entrydsm.gateway.adapterin.configuration

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "gateway")
data class GatewayRuntimeProperties(
    var cors: Cors = Cors(),
    var request: Request = Request(),
    var resilience: Resilience = Resilience(),
) {
    init {
        require(request.maxBodyBytes > 0) {
            "gateway.request.max-body-bytes must be greater than zero: ${request.maxBodyBytes}"
        }
        require(resilience.failureThreshold > 0) {
            "gateway.resilience.failure-threshold must be greater than zero: ${resilience.failureThreshold}"
        }
        require(resilience.openDurationSeconds > 0) {
            "gateway.resilience.open-duration-seconds must be greater than zero: ${resilience.openDurationSeconds}"
        }
    }

    @jakarta.annotation.PostConstruct
    fun validateAfterBinding() {
        require(request.maxBodyBytes > 0) {
            "gateway.request.max-body-bytes must be greater than zero: ${request.maxBodyBytes}"
        }
        require(resilience.failureThreshold > 0) {
            "gateway.resilience.failure-threshold must be greater than zero: ${resilience.failureThreshold}"
        }
        require(resilience.openDurationSeconds > 0) {
            "gateway.resilience.open-duration-seconds must be greater than zero: ${resilience.openDurationSeconds}"
        }
    }
    data class Cors(
        var allowedOrigins: List<String> = listOf("http://localhost:3000"),
        var allowedMethods: List<String> = listOf("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"),
        var allowedHeaders: List<String> = listOf("Authorization", "Content-Type", "X-Trace-Id"),
        var maxAgeSeconds: Long = 3600,
    )

    data class Request(
        var maxBodyBytes: Long = 10 * 1024 * 1024,
    )

    data class Resilience(
        var failureThreshold: Int = 5,
        var openDurationSeconds: Long = 30,
    )
}
