package hs.kr.entrydsm.gateway.application

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GatewayAccessPolicyTest {
    @Test
    fun leavesAuthenticationAndAuthorizationToIdentity() {
        assertTrue(GatewayAccessPolicy().isPublic("/actuator/health"))
    }
}
