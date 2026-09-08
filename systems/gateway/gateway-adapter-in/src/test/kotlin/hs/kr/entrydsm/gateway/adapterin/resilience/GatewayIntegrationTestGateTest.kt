package hs.kr.entrydsm.gateway.adapterin.resilience

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.opentest4j.TestAbortedException

class GatewayIntegrationTestGateTest {
    @Test
    fun detectsStrictIntegrationModeIgnoringCase() {
        assertTrue(GatewayIntegrationTestGate.isRequired { "true" })
        assertTrue(GatewayIntegrationTestGate.isRequired { "TRUE" })
        assertFalse(GatewayIntegrationTestGate.isRequired { "false" })
        assertFalse(GatewayIntegrationTestGate.isRequired { null })
    }

    @Test
    fun skipsUnavailableDependencyWhenStrictModeIsDisabled() {
        assertThrows(TestAbortedException::class.java) {
            GatewayIntegrationTestGate.unavailable("Redis is required", IllegalStateException("offline"), false)
        }
    }

    @Test
    fun failsUnavailableDependencyWhenStrictModeIsEnabled() {
        val cause = IllegalStateException("offline")
        val failure = assertThrows(AssertionError::class.java) {
            GatewayIntegrationTestGate.unavailable("Redis is required", cause, true)
        }

        assertSame(cause, failure.cause)
    }
}
