package hs.kr.entrydsm.gateway.adapterin.configuration

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "gateway")
data class GatewayRuntimeProperties(
    var cors: Cors = Cors(),
    var request: Request = Request(),
    var resilience: Resilience = Resilience(),
) {
    init {
        validate()
    }

    @jakarta.annotation.PostConstruct
    fun validateAfterBinding() = validate()

    private fun validate() {
        require(cors.maxAgeSeconds >= 0) {
            "gateway.cors.max-age-seconds must not be negative: ${cors.maxAgeSeconds}"
        }
        require(request.maxBodyBytes > 0) {
            "gateway.request.max-body-bytes must be greater than zero: ${request.maxBodyBytes}"
        }
        require(resilience.failureRateThreshold in 0.0..100.0) {
            "gateway.resilience.failure-rate-threshold must be between zero and one hundred: ${resilience.failureRateThreshold}"
        }
        require(resilience.slidingWindowSize > 0) {
            "gateway.resilience.sliding-window-size must be greater than zero: ${resilience.slidingWindowSize}"
        }
        require(resilience.minimumNumberOfCalls > 0) {
            "gateway.resilience.minimum-number-of-calls must be greater than zero: ${resilience.minimumNumberOfCalls}"
        }
        require(resilience.minimumNumberOfCalls <= resilience.slidingWindowSize) {
            "gateway.resilience.minimum-number-of-calls must not exceed sliding-window-size: " +
                "${resilience.minimumNumberOfCalls} > ${resilience.slidingWindowSize}"
        }
        require(resilience.waitDurationSeconds > 0) {
            "gateway.resilience.wait-duration-seconds must be greater than zero: ${resilience.waitDurationSeconds}"
        }
        require(resilience.permittedNumberOfCallsInHalfOpenState > 0) {
            "gateway.resilience.permitted-number-of-calls-in-half-open-state must be greater than zero: ${resilience.permittedNumberOfCallsInHalfOpenState}"
        }
        require(resilience.stateStore in setOf("redis", "memory")) {
            "gateway.resilience.state-store must be redis or memory: ${resilience.stateStore}"
        }
    }
    data class Cors(
        var allowedOrigins: List<String> = listOf("http://localhost:3000"),
        var allowedMethods: List<String> = listOf("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"),
        var allowedHeaders: List<String> = listOf(
            "Authorization",
            "Content-Type",
            "X-Trace-Id",
            "X-XSRF-TOKEN",
        ),
        var maxAgeSeconds: Long = 3600,
    )

    data class Request(
        var maxBodyBytes: Long = 10 * 1024 * 1024,
    )

    data class Resilience(
        var failureRateThreshold: Double = 50.0,
        var slidingWindowSize: Int = 10,
        var minimumNumberOfCalls: Int = 5,
        var waitDurationSeconds: Long = 30,
        var permittedNumberOfCallsInHalfOpenState: Int = 1,
        var stateStore: String = "redis",
    )
}
