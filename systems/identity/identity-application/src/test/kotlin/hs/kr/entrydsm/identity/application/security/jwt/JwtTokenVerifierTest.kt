package hs.kr.entrydsm.identity.application.security.jwt

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Test

class JwtTokenVerifierTest {
    @Test
    fun verifiesRefreshTokenClaims() {
        val generator = generator()
        val verified = JwtTokenVerifier(SECRET, ISSUER, Clock.fixed(NOW, UTC))
            .verifyRefreshToken(generator.generateRefreshToken("user_123").value)

        assertEquals(123L, verified.userId)
        assertEquals(0L, verified.tokenVersion)
        assertEquals(NOW.plus(JwtTokenGenerator.REFRESH_TOKEN_TTL), verified.expiresAt)
    }

    @Test(expected = JwtTokenVerificationException::class)
    fun rejectsAccessTokenAsRefreshToken() {
        JwtTokenVerifier(SECRET, ISSUER, Clock.fixed(NOW, UTC))
            .verifyRefreshToken(generator().generateAccessToken("user_123").value)
    }

    @Test(expected = JwtTokenVerificationException::class)
    fun rejectsTokenSignedWithAnotherSecret() {
        val token = generator("abcdefghijklmnopqrstuvwxyz123456")
            .generateRefreshToken("user_123")
            .value

        JwtTokenVerifier(SECRET, ISSUER, Clock.fixed(NOW, UTC)).verifyRefreshToken(token)
    }

    @Test
    fun rejectsExpiredRefreshTokenWithExpiredReason() {
        val token = JwtTokenGenerator(
            SECRET,
            ISSUER,
            Clock.fixed(NOW.minus(JwtTokenGenerator.REFRESH_TOKEN_TTL).minusSeconds(1), UTC),
        ).generateRefreshToken("user_123").value

        val thrown = try {
            JwtTokenVerifier(SECRET, ISSUER, Clock.fixed(NOW, UTC)).verifyRefreshToken(token)
            null
        } catch (exception: JwtTokenVerificationException) {
            exception
        }

        assertEquals(JwtTokenVerificationException.Reason.EXPIRED, thrown?.reason)
    }

    @Test
    fun rejectsLegacyRedisVersionToken() {
        val token = io.jsonwebtoken.Jwts.builder()
            .issuer(ISSUER).subject("user_123").id("legacy-token")
            .claim(JwtTokenGenerator.TOKEN_TYPE_CLAIM, "refresh")
            .claim(JwtTokenGenerator.TOKEN_VERSION_CLAIM, 0L)
            .expiration(java.util.Date.from(NOW.plusSeconds(60)))
            .signWith(io.jsonwebtoken.security.Keys.hmacShaKeyFor(SECRET.toByteArray()), io.jsonwebtoken.Jwts.SIG.HS256)
            .compact()
        val thrown = org.junit.Assert.assertThrows(JwtTokenVerificationException::class.java) {
            JwtTokenVerifier(SECRET, ISSUER, Clock.fixed(NOW, UTC)).verifyRefreshToken(token)
        }
        assertEquals(JwtTokenVerificationException.Reason.INVALID, thrown.reason)
    }

    private fun generator(secret: String = SECRET): JwtTokenGenerator = JwtTokenGenerator(
        secret = secret,
        issuer = ISSUER,
        clock = Clock.fixed(NOW, UTC),
    )

    private companion object {
        const val SECRET = "01234567890123456789012345678901"
        const val ISSUER = "entrydsm-identity"
        val NOW: Instant = Instant.parse("2026-06-11T10:00:00Z")
        val UTC = ZoneOffset.UTC
    }
}
