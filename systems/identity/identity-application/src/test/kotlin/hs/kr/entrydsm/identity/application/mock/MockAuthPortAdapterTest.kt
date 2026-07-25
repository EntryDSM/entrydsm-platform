package hs.kr.entrydsm.identity.application.mock

import com.nimbusds.jose.crypto.MACVerifier
import com.nimbusds.jwt.SignedJWT
import hs.kr.entrydsm.identity.application.port.`in`.command.LoginCommand
import hs.kr.entrydsm.identity.application.security.jwt.JwtTokenGenerator
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MockAuthPortAdapterTest {
    @Test
    fun mockLoginIssuesDistinctVerifiableTokens() {
        val adapter = MockAuthPortAdapter(
            JwtTokenGenerator(
                secret = SECRET,
                issuer = ISSUER,
                clock = Clock.fixed(NOW, UTC),
            )
        )

        val first = adapter.login(LoginCommand("entry", "password123!"))
        val second = adapter.login(LoginCommand("entry", "password123!"))

        assertTrue(SignedJWT.parse(first.accessToken.value).verify(MACVerifier(SECRET.toByteArray())))
        assertTrue(SignedJWT.parse(first.refreshToken.value).verify(MACVerifier(SECRET.toByteArray())))
        assertNotEquals(first.accessToken.value, second.accessToken.value)
        assertNotEquals(first.refreshToken.value, second.refreshToken.value)
    }

    private companion object {
        const val SECRET = "01234567890123456789012345678901"
        const val ISSUER = "entrydsm-identity"
        val NOW: Instant = Instant.parse("2026-06-11T10:00:00Z")
        val UTC = ZoneOffset.UTC
    }
}
