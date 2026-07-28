package hs.kr.entrydsm.gateway.adapterin.configuration

import org.junit.jupiter.api.Assertions.assertEquals
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
}
