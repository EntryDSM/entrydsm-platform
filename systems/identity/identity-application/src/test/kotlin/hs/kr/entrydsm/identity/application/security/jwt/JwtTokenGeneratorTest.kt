package hs.kr.entrydsm.identity.application.security.jwt

import java.nio.charset.StandardCharsets
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Base64
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
        val payload = decodePayload(token.value)

        assertEquals(TokenType.ACCESS, token.type)
        assertEquals(fixedNow, token.issuedAt)
        assertEquals(fixedNow.plus(JwtTokenGenerator.ACCESS_TOKEN_TTL), token.expiresAt)
        assertTrue(token.value.split(".").size == 3)
        assertTrue(payload.contains("\"iss\":\"entrydsm-identity\""))
        assertTrue(payload.contains("\"sub\":\"user_123\""))
        assertTrue(payload.contains("\"typ\":\"access\""))
        assertTrue(payload.contains("\"iat\":1781172000"))
        assertTrue(payload.contains("\"exp\":1781179200"))
    }

    @Test
    fun generateRefreshTokenUsesSevenDayExpiration() {
        val token = generator.generateRefreshToken("user_123")
        val payload = decodePayload(token.value)

        assertEquals(TokenType.REFRESH, token.type)
        assertEquals(fixedNow, token.issuedAt)
        assertEquals(fixedNow.plus(JwtTokenGenerator.REFRESH_TOKEN_TTL), token.expiresAt)
        assertTrue(payload.contains("\"typ\":\"refresh\""))
        assertTrue(payload.contains("\"exp\":1781776800"))
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

    private fun decodePayload(token: String): String {
        val payload = token.split(".")[1]
        val decoded = Base64.getUrlDecoder().decode(payload)
        return String(decoded, StandardCharsets.UTF_8)
    }
}
