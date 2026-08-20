package hs.kr.entrydsm.identity.config.security

import hs.kr.entrydsm.identity.application.security.jwt.JwtTokenGenerator
import hs.kr.entrydsm.identity.application.security.AuthenticatedUser
import hs.kr.entrydsm.identity.application.port.out.AccountQueryPort
import hs.kr.entrydsm.identity.application.port.out.RefreshTokenRevocationStore
import hs.kr.entrydsm.identity.application.port.out.RefreshTokenStoreUnavailableException
import hs.kr.entrydsm.identity.domain.enum.AccountStatus
import hs.kr.entrydsm.identity.domain.model.Account
import jakarta.servlet.FilterChain
import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletResponse
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import jakarta.servlet.http.Cookie
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
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class JwtFilterTest {
    private val tokenVersions = mutableMapOf<Long, Long>()

    @Test
    fun validAccessTokenSetsSecurityContextAndContinuesChain() {
        val result = runFilter(accessToken())

        assertEquals(200, result.status)
        assertTrue(result.chainInvoked)
        assertEquals(AuthenticatedUser(123L), result.authentication?.principal)
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
    fun accessTokenIssuedBeforeRevocationReturnsUnauthorized() {
        tokenVersions[123L] = 1L

        val result = runFilter(accessToken())

        assertEquals(401, result.status)
        assertFalse(result.chainInvoked)
        assertNull(result.authentication)
    }

    @Test
    fun inactiveAccountAccessTokenReturnsUnauthorized() {
        val result = runFilter(
            token = accessToken(),
            account = account(AccountStatus.INACTIVE),
        )

        assertEquals(401, result.status)
        assertFalse(result.chainInvoked)
        assertNull(result.authentication)
    }

    @Test
    fun deletedAccountAccessTokenReturnsUnauthorized() {
        val result = runFilter(
            token = accessToken(),
            account = account(AccountStatus.DELETED),
        )

        assertEquals(401, result.status)
        assertFalse(result.chainInvoked)
        assertNull(result.authentication)
    }

    @Test
    fun redisFailureReturnsServiceUnavailable() {
        val result = runFilter(
            token = accessToken(),
            revocationStore = object : RefreshTokenRevocationStore {
                override fun currentVersion(userId: Long): Long =
                    throw RefreshTokenStoreUnavailableException(IllegalStateException("redis unavailable"))

                override fun revokeAll(userId: Long) = Unit
            },
        )

        assertEquals(503, result.status)
        assertTrue(result.body.contains("REDIS_UNAVAILABLE"))
        assertTrue(result.contentType.orEmpty().startsWith("application/json"))
        assertFalse(result.chainInvoked)
        assertNull(result.authentication)
    }

    @Test
    fun tamperedAndMalformedTokensReturnUnauthorized() {
        val tamperedToken = JwtTokenGenerator(
            secret = "abcdefghijklmnopqrstuvwxyz123456",
            issuer = ISSUER,
            clock = Clock.fixed(FIXED_NOW, UTC),
        ).generateAccessToken("user_123").value
        val tamperedResult = runFilter(tamperedToken)
        val malformedResult = runFilter("not-a-jwt")

        assertEquals(401, tamperedResult.status)
        assertEquals(401, malformedResult.status)
        assertFalse(tamperedResult.chainInvoked)
        assertFalse(malformedResult.chainInvoked)
    }

    @Test
    fun tokenWithInvalidPrincipalFormatReturnsUnauthorized() {
        val invalidPrincipal = JwtTokenGenerator(
            secret = SECRET,
            issuer = ISSUER,
            clock = Clock.fixed(FIXED_NOW, UTC),
        ).generateAccessToken("admin").value

        val result = runFilter(invalidPrincipal)

        assertEquals(401, result.status)
        assertFalse(result.chainInvoked)
        assertNull(result.authentication)
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

    @Test
    fun everyPublicAuthEndpointSkipsInvalidCookieValidation() {
        val publicPaths = listOf(
            "/api/identity/v11/auth/signup",
            "/api/identity/v11/auth/login",
            "/api/identity/v11/auth/token",
        )

        publicPaths.forEach { path ->
            val result = runFilter(token = "not-a-jwt", path = path, useCookie = true)

            assertEquals(200, result.status)
            assertTrue(result.chainInvoked)
            assertNull(result.authentication)
        }
    }

    private fun runFilter(
        token: String?,
        path: String = "/api/identity/v11/accounts/me",
        useCookie: Boolean = false,
        revocationStore: RefreshTokenRevocationStore = defaultRevocationStore(),
        account: Account? = account(AccountStatus.ACTIVE),
    ): FilterResult {
        SecurityContextHolder.clearContext()
        val request = MockHttpServletRequest("GET", path)
        if (token != null) {
            if (useCookie) {
                request.setCookies(Cookie("access_token", token))
            } else {
                request.addHeader("Authorization", "Bearer $token")
            }
        }
        val response = MockHttpServletResponse()
        val chain = RecordingFilterChain()
        jwtFilter(revocationStore, account).doFilter(request, response, chain)
        return FilterResult(
            status = response.status,
            chainInvoked = chain.invoked,
            authentication = SecurityContextHolder.getContext().authentication,
            body = response.contentAsString,
            contentType = response.contentType,
        )
    }

    private fun jwtFilter(
        revocationStore: RefreshTokenRevocationStore,
        account: Account?,
    ): JwtFilter = JwtFilter(
        jwtProperties = JwtProperties(secret = SECRET, issuer = ISSUER),
        clock = Clock.fixed(FIXED_NOW, UTC),
        authenticationEntryPoint = unauthorizedEntryPoint,
        accountQueryPort = object : AccountQueryPort {
            override fun findByLoginId(loginId: String): Account? = account

            override fun findByUserId(userId: Long): Account? = account
        },
        refreshTokenRevocationStore = revocationStore,
    )

    private fun account(status: AccountStatus): Account = mock(Account::class.java).also {
        `when`(it.status).thenReturn(status)
    }

    private fun defaultRevocationStore(): RefreshTokenRevocationStore = object : RefreshTokenRevocationStore {
        override fun currentVersion(userId: Long): Long = tokenVersions[userId] ?: 0L

        override fun revokeAll(userId: Long) {
            tokenVersions[userId] = currentVersion(userId) + 1
        }
    }

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
        val body: String = "",
        val contentType: String? = null,
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
