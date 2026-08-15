package hs.kr.entrydsm.gateway.application

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GatewayAccessPolicyTest {
    @Test
    fun leavesAuthenticationAndAuthorizationToIdentity() {
        assertTrue(GatewayAccessPolicy().isPublic("/actuator/health"))
        assertTrue(GatewayAccessPolicy().isPublic("/actuator/health/details"))
        assertFalse(GatewayAccessPolicy().isPublic("/actuator/healthcheck"))
        assertFalse(GatewayAccessPolicy().isPublic("/api/users"))
    }
}
