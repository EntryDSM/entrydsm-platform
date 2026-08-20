package hs.kr.entrydsm.gateway.adapterin.configuration

import jakarta.annotation.PostConstruct
import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "gateway.downstream")
data class DownstreamClientPolicy(
    var connectTimeoutMillis: Int = 2_000,
    var responseTimeoutMillis: Long = 5_000,
    var retries: Int = 2,
    var retryMethods: List<String> = listOf("GET", "HEAD", "OPTIONS"),
    var retryFirstBackoffMillis: Long = 50,
    var retryMaxBackoffMillis: Long = 1_000,
    var retryBackoffFactor: Int = 2,
    var retryBackoffBasedOnPreviousValue: Boolean = false,
    var retryJitterRandomFactor: Double = 0.5,
) {
    @PostConstruct
    fun validate() {
        require(connectTimeoutMillis > 0) {
            "gateway.downstream.connect-timeout-millis must be greater than zero: $connectTimeoutMillis"
        }
        require(responseTimeoutMillis > 0) {
            "gateway.downstream.response-timeout-millis must be greater than zero: $responseTimeoutMillis"
        }
        require(retries >= 0) {
            "gateway.downstream.retries must not be negative: $retries"
        }
        require(retryMethods.all { it in SAFE_RETRY_METHODS }) {
            "gateway.downstream.retry-methods must contain only GET, HEAD or OPTIONS: $retryMethods"
        }
        require(retryFirstBackoffMillis > 0) {
            "gateway.downstream.retry-first-backoff-millis must be greater than zero: $retryFirstBackoffMillis"
        }
        require(retryMaxBackoffMillis >= retryFirstBackoffMillis) {
            "gateway.downstream.retry-max-backoff-millis must be greater than or equal to retry-first-backoff-millis: $retryMaxBackoffMillis"
        }
        require(retryBackoffFactor > 0) {
            "gateway.downstream.retry-backoff-factor must be greater than zero: $retryBackoffFactor"
        }
        require(retryJitterRandomFactor in 0.0..1.0) {
            "gateway.downstream.retry-jitter-random-factor must be between zero and one: $retryJitterRandomFactor"
        }
    }

    private companion object {
        val SAFE_RETRY_METHODS = setOf("GET", "HEAD", "OPTIONS")
    }
}
