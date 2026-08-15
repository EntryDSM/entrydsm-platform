package hs.kr.entrydsm.gateway.adapterin.configuration

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class DownstreamClientPolicyTest {
    @Test
    fun definesSafeDownstreamDefaults() {
        val policy = DownstreamClientPolicy()

        assertEquals(2_000, policy.connectTimeoutMillis)
        assertEquals(5_000, policy.responseTimeoutMillis)
        assertEquals(2, policy.retries)
        assertEquals(listOf("GET", "HEAD", "OPTIONS"), policy.retryMethods)
        assertEquals(50, policy.retryFirstBackoffMillis)
        assertEquals(1_000, policy.retryMaxBackoffMillis)
        assertEquals(0.5, policy.retryJitterRandomFactor)
    }

    @Test
    fun rejectsInvalidRetryBackoffPolicy() {
        assertThrows(IllegalArgumentException::class.java) {
            DownstreamClientPolicy(retryJitterRandomFactor = 1.1).validate()
        }
    }

    @Test
    fun rejectsInvalidTimeoutAndRetryCount() {
        assertInvalid("connect-timeout-millis") {
            DownstreamClientPolicy(connectTimeoutMillis = 0).validate()
        }
        assertInvalid("retries") {
            DownstreamClientPolicy(retries = -1).validate()
        }
        assertEquals(0, DownstreamClientPolicy(retries = 0).retries)
    }

    @Test
    fun rejectsInvalidRetryMethodsAndBackoffBounds() {
        assertInvalid("retry-methods") {
            DownstreamClientPolicy(retryMethods = listOf("POST")).validate()
        }
        assertInvalid("retry-first-backoff-millis") {
            DownstreamClientPolicy(retryFirstBackoffMillis = 0).validate()
        }
        assertInvalid("retry-max-backoff-millis") {
            DownstreamClientPolicy(
                retryFirstBackoffMillis = 100,
                retryMaxBackoffMillis = 99,
            ).validate()
        }
        assertInvalid("retry-backoff-factor") {
            DownstreamClientPolicy(retryBackoffFactor = 0).validate()
        }
    }

    @Test
    fun acceptsRetryPolicyBoundaryValues() {
        assertEquals(
            listOf("GET", "HEAD", "OPTIONS"),
            DownstreamClientPolicy(retryMethods = listOf("GET", "HEAD", "OPTIONS")).retryMethods,
        )
        assertEquals(
            100L,
            DownstreamClientPolicy(
                retryFirstBackoffMillis = 100,
                retryMaxBackoffMillis = 100,
            ).retryMaxBackoffMillis,
        )
        assertEquals(1, DownstreamClientPolicy(retryBackoffFactor = 1).retryBackoffFactor)
    }

    private fun assertInvalid(field: String, block: () -> Unit) {
        val exception = assertThrows(IllegalArgumentException::class.java, block)
        assertTrue(exception.message.orEmpty().contains(field))
    }
}
