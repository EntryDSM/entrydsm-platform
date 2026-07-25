package hs.kr.entrydsm.identity.config.security

import hs.kr.entrydsm.identity.application.security.jwt.JwtTokenGenerator
import jakarta.servlet.FilterChain
import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletResponse
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.AuthenticationException
import org.springframework.security.web.AuthenticationEntryPoint

class JwtFilterTest {
    @Test
    fun validAccessTokenSetsSecurityContextAndContinuesChain() {
        val result = runFilter(accessToken())

        assertEquals(200, result.status)
        assertTrue(result.chainInvoked)
        assertEquals("user_123", result.authentication?.name)
    }

    @Test
    fun expiredAccessTokenReturnsUnauthorizedWithoutContinuingChain() {
        val expired = JwtTokenGenerator(
            secret = SECRET,
            issuer = ISSUER,
            clock = Clock.fixed(FIXED_NOW.minusSeconds(7201), UTC),
        ).generateAccessToken("user_123").value

        val result = runFilter(expired)

        assertEquals(401, result.status)
        assertFalse(result.chainInvoked)
        assertNull(result.authentication)
    }

    @Test
    fun tamperedAndMalformedTokensReturnUnauthorized() {
        val tamperedResult = runFilter(accessToken().dropLast(1) + "x")
        val malformedResult = runFilter("not-a-jwt")

        assertEquals(401, tamperedResult.status)
        assertEquals(401, malformedResult.status)
        assertFalse(tamperedResult.chainInvoked)
        assertFalse(malformedResult.chainInvoked)
    }

    @Test
    fun missingTokenContinuesChainWithoutAuthentication() {
        val result = runFilter(null)

        assertEquals(200, result.status)
        assertTrue(result.chainInvoked)
        assertNull(result.authentication)
    }

    @Test
    fun publicAuthEndpointSkipsInvalidTokenValidation() {
        val result = runFilter(
            token = "not-a-jwt",
            path = "/api/identity/v11/auth/login",
        )

        assertEquals(200, result.status)
        assertTrue(result.chainInvoked)
        assertNull(result.authentication)
    }

    private fun runFilter(
        token: String?,
        path: String = "/api/identity/v11/accounts/me",
    ): FilterResult {
        SecurityContextHolder.clearContext()
        val request = MockHttpServletRequest("GET", path)
        if (token != null) {
            request.addHeader("Authorization", "Bearer $token")
        }
        val response = MockHttpServletResponse()
        val chain = RecordingFilterChain()
        jwtFilter().doFilter(request, response, chain)
        return FilterResult(
            status = response.status,
            chainInvoked = chain.invoked,
            authentication = SecurityContextHolder.getContext().authentication,
        )
    }

    private fun jwtFilter(): JwtFilter = JwtFilter(
        jwtProperties = JwtProperties(secret = SECRET, issuer = ISSUER),
        clock = Clock.fixed(FIXED_NOW, UTC),
        authenticationEntryPoint = unauthorizedEntryPoint,
    )

    private fun accessToken(): String = JwtTokenGenerator(
        secret = SECRET,
        issuer = ISSUER,
        clock = Clock.fixed(FIXED_NOW, UTC),
    ).generateAccessToken("user_123").value

    private class RecordingFilterChain : FilterChain {
        var invoked: Boolean = false

        override fun doFilter(request: ServletRequest, response: ServletResponse) {
            invoked = true
        }
    }

    private data class FilterResult(
        val status: Int,
        val chainInvoked: Boolean,
        val authentication: Authentication?,
    )

    private companion object {
        const val SECRET = "01234567890123456789012345678901"
        const val ISSUER = "entrydsm-identity"
        val FIXED_NOW: Instant = Instant.parse("2026-06-11T10:00:00Z")
        val UTC = ZoneOffset.UTC
        val unauthorizedEntryPoint = object : AuthenticationEntryPoint {
            override fun commence(
                request: jakarta.servlet.http.HttpServletRequest,
                response: jakarta.servlet.http.HttpServletResponse,
                authException: AuthenticationException,
            ) {
                response.sendError(401)
            }
        }
    }
}
