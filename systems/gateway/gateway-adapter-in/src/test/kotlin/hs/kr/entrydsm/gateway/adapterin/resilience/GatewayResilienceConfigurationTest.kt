package hs.kr.entrydsm.gateway.adapterin.resilience

import hs.kr.entrydsm.gateway.adapterin.configuration.GatewayRuntimeProperties
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Duration

class GatewayResilienceConfigurationTest {
    @Test
    fun mapsRuntimePolicyToCircuitBreakerConfig() {
        val properties = GatewayRuntimeProperties(
            resilience = GatewayRuntimeProperties.Resilience(
                failureRateThreshold = 25.0,
                slidingWindowSize = 20,
                minimumNumberOfCalls = 7,
                waitDurationSeconds = 12,
                permittedNumberOfCallsInHalfOpenState = 3,
            ),
        )

        val registry = GatewayResilienceConfiguration().gatewayCircuitBreakerRegistry(properties)
        val config = registry.circuitBreaker("identity").circuitBreakerConfig

        assertEquals(25.0f, config.failureRateThreshold)
        assertEquals(20, config.slidingWindowSize)
        assertEquals(7, config.minimumNumberOfCalls)
        assertEquals(Duration.ofSeconds(12).toMillis(), config.waitIntervalFunctionInOpenState.apply(1))
        assertEquals(3, config.permittedNumberOfCallsInHalfOpenState)
    }
}
