package hs.kr.entrydsm.gateway.adapterin.configuration

import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GatewayRuntimePropertiesTest {
    @Test
    fun rejectsInvalidRuntimeLimitsAtStartup() {
        assertInvalid("gateway.request.max-body-bytes") {
            GatewayRuntimeProperties(request = GatewayRuntimeProperties.Request(maxBodyBytes = 0))
        }
        assertInvalid("gateway.cors.max-age-seconds") {
            GatewayRuntimeProperties(cors = GatewayRuntimeProperties.Cors(maxAgeSeconds = -1))
        }
    }

    @Test
    fun acceptsZeroCorsMaxAge() {
        assertDoesNotThrow {
            GatewayRuntimeProperties(cors = GatewayRuntimeProperties.Cors(maxAgeSeconds = 0))
        }
    }

    @Test
    fun rejectsInvalidResiliencePolicy() {
        assertInvalid("gateway.resilience.failure-rate-threshold") {
            GatewayRuntimeProperties(
                resilience = GatewayRuntimeProperties.Resilience(failureRateThreshold = 100.1),
            )
        }
        assertInvalid("gateway.resilience.minimum-number-of-calls") {
            GatewayRuntimeProperties(
                resilience = GatewayRuntimeProperties.Resilience(minimumNumberOfCalls = 11),
            )
        }
    }

    private fun assertInvalid(field: String, block: () -> Unit) {
        val exception = assertThrows(IllegalArgumentException::class.java, block)
        assertTrue(exception.message.orEmpty().contains(field))
    }
}
