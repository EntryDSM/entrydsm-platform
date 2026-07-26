package hs.kr.entrydsm.identity.application.security.jwt

import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Date
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JwtTokenGeneratorTest {
    private val fixedNow = Instant.parse("2026-06-11T10:00:00Z")
    private val generator = JwtTokenGenerator(
        secret = "01234567890123456789012345678901",
        issuer = "entrydsm-identity",
        clock = Clock.fixed(fixedNow, ZoneOffset.UTC),
    )

    @Test
    fun generateAccessTokenUsesTwoHourExpiration() {
        val token = generator.generateAccessToken("user_123")
        val payload = parse(token.value)

        assertEquals(TokenType.ACCESS, token.type)
        assertEquals(fixedNow, token.issuedAt)
        assertEquals(fixedNow.plus(JwtTokenGenerator.ACCESS_TOKEN_TTL), token.expiresAt)
        assertTrue(token.value.split(".").size == 3)
        assertEquals("entrydsm-identity", payload.issuer)
        assertEquals("user_123", payload.subject)
        assertEquals("access", payload[JwtTokenGenerator.TOKEN_TYPE_CLAIM])
        assertEquals(fixedNow, payload.issuedAt.toInstant())
        assertEquals(fixedNow.plus(JwtTokenGenerator.ACCESS_TOKEN_TTL), payload.expiration.toInstant())
    }

    @Test
    fun generateRefreshTokenUsesSevenDayExpiration() {
        val token = generator.generateRefreshToken("user_123")
        val payload = parse(token.value)

        assertEquals(TokenType.REFRESH, token.type)
        assertEquals(fixedNow, token.issuedAt)
        assertEquals(fixedNow.plus(JwtTokenGenerator.REFRESH_TOKEN_TTL), token.expiresAt)
        assertEquals("refresh", payload[JwtTokenGenerator.TOKEN_TYPE_CLAIM])
        assertEquals(fixedNow.plus(JwtTokenGenerator.REFRESH_TOKEN_TTL), payload.expiration.toInstant())
    }

    @Test
    fun generatedTokenIsVerifiableByJjwt() {
        val token = generator.generateAccessToken("user_123")
        assertEquals("user_123", parse(token.value).subject)
    }

    @Test
    fun tokenToStringDoesNotExposeRawJwt() {
        val token = generator.generateAccessToken("user_123")

        assertTrue(!token.toString().contains(token.value))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectBlankSubject() {
        generator.generateAccessToken(" ")
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectShortSecret() {
        JwtTokenGenerator(
            secret = "short",
            issuer = "entrydsm-identity",
            clock = Clock.fixed(fixedNow, ZoneOffset.UTC),
        )
    }

    private fun parse(token: String) = Jwts.parser()
        .verifyWith(Keys.hmacShaKeyFor(SECRET.toByteArray()))
        .clock(io.jsonwebtoken.Clock { Date.from(fixedNow) })
        .build()
        .parseSignedClaims(token)
        .payload

    private companion object {
        const val SECRET = "01234567890123456789012345678901"
    }
}
