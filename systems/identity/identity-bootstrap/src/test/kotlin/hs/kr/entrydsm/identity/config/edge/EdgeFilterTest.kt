package hs.kr.entrydsm.identity.config.edge

import hs.kr.entrydsm.identity.application.port.out.AccountQueryPort
import hs.kr.entrydsm.identity.application.port.out.RefreshTokenRevocationStore
import hs.kr.entrydsm.identity.application.port.out.RefreshTokenStoreUnavailableException
import hs.kr.entrydsm.identity.application.security.jwt.JwtTokenGenerator
import hs.kr.entrydsm.identity.config.security.AccessTokenAuthenticator
import hs.kr.entrydsm.identity.config.security.JwtProperties
import hs.kr.entrydsm.identity.domain.enum.AccountStatus
import hs.kr.entrydsm.identity.domain.enum.Role
import hs.kr.entrydsm.identity.domain.model.Account
import jakarta.servlet.FilterChain
import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletResponse
import jakarta.servlet.http.Cookie
import jakarta.servlet.http.HttpServletRequest
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse

/** 게이트웨이가 앞단에서 하던 일을 서블릿 필터가 그대로 하는지 확인한다. */
class EdgeAccessFilterTest {
    @Test
    fun removesClientSuppliedTrustedHeadersAndSetsClientIp() {
        val request = request("/api/notification/v11/notifications/notification").apply {
            remoteAddr = "10.0.0.5"
            addHeader(EdgeContract.USER_ID_HEADER, "9999")
            addHeader(EdgeContract.USER_ROLE_HEADER, "ADMIN")
            addHeader(EdgeContract.SENSITIVE_AGREE_HEADER, "true")
            addHeader(EdgeContract.CLIENT_IP_HEADER, "1.1.1.1")
            addHeader(EdgeContract.FORWARDED_FOR_HEADER, "203.0.113.7, 10.0.0.5")
        }

        val chain = runFilter(request)

        val forwarded = requireNotNull(chain.request)
        assertNull(forwarded.getHeader(EdgeContract.USER_ID_HEADER))
        assertNull(forwarded.getHeader(EdgeContract.USER_ROLE_HEADER))
        assertNull(forwarded.getHeader(EdgeContract.SENSITIVE_AGREE_HEADER))
        assertNull(forwarded.getHeader(EdgeContract.FORWARDED_FOR_HEADER))
        assertEquals("203.0.113.7", forwarded.getHeader(EdgeContract.CLIENT_IP_HEADER))
    }

    @Test
    fun keepsPeerAddressWhenItIsNotInternal() {
        val request = request("/api/monitor/v11/collect/session").apply {
            remoteAddr = "198.51.100.9"
            addHeader(EdgeContract.FORWARDED_FOR_HEADER, "203.0.113.7")
        }

        val chain = runFilter(request)

        assertEquals("198.51.100.9", chain.request?.getHeader(EdgeContract.CLIENT_IP_HEADER))
    }

    @Test
    fun rejectsPathsThatWereNeverRouted() {
        val response = MockHttpServletResponse()
        val chain = RecordingFilterChain()

        filter().doFilter(request("/nope"), response, chain)

        assertEquals(404, response.status)
        assertTrue(response.contentAsString.contains("ROUTE_NOT_FOUND"))
        assertEquals("no-store", response.getHeader("Cache-Control"))
        assertFalse(chain.invoked)
    }

    @Test
    fun fillsTrustedHeadersFromTheAccessTokenCookie() {
        val request = request("/api/v11/admin/applicants").apply {
            setCookies(Cookie(EdgeContract.ACCESS_TOKEN_COOKIE, accessToken()))
        }

        val chain = runFilter(request, account = account(Role.ADMIN, isSensitiveAgree = true))

        val forwarded = requireNotNull(chain.request)
        assertEquals("123", forwarded.getHeader(EdgeContract.USER_ID_HEADER))
        assertEquals("ADMIN", forwarded.getHeader(EdgeContract.USER_ROLE_HEADER))
        assertEquals("123", forwarded.getHeader(EdgeContract.LEGACY_USER_ID_HEADER))
        assertEquals("true", forwarded.getHeader(EdgeContract.SENSITIVE_AGREE_HEADER))
    }

    @Test
    fun identityPathsAuthenticateThemselvesAndGetNoUserHeaders() {
        val request = request("/api/identity/v11/accounts/me").apply {
            setCookies(Cookie(EdgeContract.ACCESS_TOKEN_COOKIE, accessToken()))
        }

        val chain = runFilter(request)

        assertTrue(chain.invoked)
        assertNull(chain.request?.getHeader(EdgeContract.USER_ID_HEADER))
    }

    @Test
    fun requestsWithoutCookiePassThroughWithoutUserHeaders() {
        val chain = runFilter(request("/api/notification/v11/notifications/notification"))

        assertTrue(chain.invoked)
        assertNull(chain.request?.getHeader(EdgeContract.USER_ID_HEADER))
    }

    /** 공개 엔드포인트라도 못 믿을 쿠키를 들고 오면 401 이다. 게이트웨이와 같은 판단이다. */
    @Test
    fun invalidAccessTokenCookieIsRejected() {
        val response = MockHttpServletResponse()
        val chain = RecordingFilterChain()
        val request = request("/api/notification/v11/notifications/notification").apply {
            setCookies(Cookie(EdgeContract.ACCESS_TOKEN_COOKIE, "not-a-jwt"))
        }

        filter().doFilter(request, response, chain)

        assertEquals(401, response.status)
        assertTrue(response.contentAsString.contains("AUTH_UNAUTHORIZED"))
        assertFalse(chain.invoked)
    }

    @Test
    fun tokenStoreFailureIsReportedAsIdentityUnavailable() {
        val response = MockHttpServletResponse()
        val chain = RecordingFilterChain()
        val request = request("/api/v11/admin/applicants").apply {
            setCookies(Cookie(EdgeContract.ACCESS_TOKEN_COOKIE, accessToken()))
        }

        filter(
            revocationStore = object : RefreshTokenRevocationStore {
                override fun currentVersion(userId: Long): Long =
                    throw RefreshTokenStoreUnavailableException(IllegalStateException("redis unavailable"))

                override fun revokeAll(userId: Long) = Unit
            },
        ).doFilter(request, response, chain)

        assertEquals(503, response.status)
        assertTrue(response.contentAsString.contains("IDENTITY_UNAVAILABLE"))
        assertFalse(chain.invoked)
    }

    private fun runFilter(
        request: MockHttpServletRequest,
        account: Account = account(Role.STUDENT, isSensitiveAgree = false),
    ): RecordingFilterChain {
        val chain = RecordingFilterChain()
        filter(account = account).doFilter(request, MockHttpServletResponse(), chain)
        return chain
    }

    private fun filter(
        account: Account = account(Role.STUDENT, isSensitiveAgree = false),
        revocationStore: RefreshTokenRevocationStore = RefreshTokenRevocationStoreStub(),
    ) = EdgeAccessFilter(
        AccessTokenAuthenticator(
            jwtProperties = JwtProperties(secret = SECRET, issuer = ISSUER),
            clock = Clock.fixed(FIXED_NOW, ZoneOffset.UTC),
            accountQueryPort = object : AccountQueryPort {
                override fun findByLoginId(loginId: String): Account = account

                override fun findByUserId(userId: Long): Account = account
            },
            refreshTokenRevocationStore = revocationStore,
        ),
    )

    private fun request(path: String) = MockHttpServletRequest("GET", path)

    private fun account(role: Role, isSensitiveAgree: Boolean): Account = mock(Account::class.java).also {
        `when`(it.status).thenReturn(AccountStatus.ACTIVE)
        `when`(it.role).thenReturn(role)
        `when`(it.isSensitiveAgree).thenReturn(isSensitiveAgree)
    }

    private fun accessToken(): String = JwtTokenGenerator(
        secret = SECRET,
        issuer = ISSUER,
        clock = Clock.fixed(FIXED_NOW, ZoneOffset.UTC),
    ).generateAccessToken("user_123").value

    private class RefreshTokenRevocationStoreStub : RefreshTokenRevocationStore {
        override fun currentVersion(userId: Long): Long = 0L

        override fun revokeAll(userId: Long) = Unit
    }

    class RecordingFilterChain : FilterChain {
        var invoked: Boolean = false
        var request: HttpServletRequest? = null

        override fun doFilter(request: ServletRequest, response: ServletResponse) {
            invoked = true
            this.request = request as HttpServletRequest
        }
    }

    private companion object {
        const val SECRET = "01234567890123456789012345678901"
        const val ISSUER = "entrydsm-identity"
        val FIXED_NOW: Instant = Instant.parse("2026-06-11T10:00:00Z")
    }
}
