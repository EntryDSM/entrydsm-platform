package hs.kr.entrydsm.observability.adapterin.web.security

import hs.kr.entrydsm.observability.domain.enum.ErrorCode
import hs.kr.entrydsm.observability.domain.exception.MonitorDomainException
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import java.nio.charset.StandardCharsets
import java.util.Date
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse

class JwtAuthInterceptorTest {
    private val secret = "test-secret-key-at-least-32-bytes-long!!"
    private val issuer = "entrydsm-test"
    private val key = Keys.hmacShaKeyFor(secret.toByteArray(StandardCharsets.UTF_8))
    private val properties = JwtAuthProperties().apply {
        this.secret = this@JwtAuthInterceptorTest.secret
        this.issuer = this@JwtAuthInterceptorTest.issuer
    }
    private val interceptor = JwtAuthInterceptor(properties)

    private fun token(role: String, expiresInMillis: Long = 60_000, issuer: String = this.issuer): String =
        Jwts.builder()
            .issuer(issuer)
            .claim("role", role)
            .expiration(Date(System.currentTimeMillis() + expiresInMillis))
            .signWith(key)
            .compact()

    private fun requestWithToken(token: String?): MockHttpServletRequest =
        MockHttpServletRequest().apply { token?.let { addHeader("Authorization", "Bearer $it") } }

    @Test
    fun allowsAdminToken() {
        val allowed = interceptor.preHandle(requestWithToken(token("ADMIN")), MockHttpServletResponse(), Any())
        assertTrue(allowed)
    }

    private fun rejectionCode(token: String?): ErrorCode =
        assertThrows(MonitorDomainException::class.java) {
            interceptor.preHandle(requestWithToken(token), MockHttpServletResponse(), Any())
        }.errorCode

    @Test
    fun rejectsMissingTokenWithUnauthorized() {
        assertEquals(ErrorCode.UNAUTHORIZED, rejectionCode(null))
    }

    @Test
    fun rejectsNonAdminRoleWithForbidden() {
        assertEquals(ErrorCode.FORBIDDEN, rejectionCode(token("USER")))
    }

    @Test
    fun rejectsExpiredTokenWithUnauthorized() {
        assertEquals(ErrorCode.UNAUTHORIZED, rejectionCode(token("ADMIN", expiresInMillis = -1000)))
    }

    @Test
    fun rejectsWrongIssuerWithUnauthorized() {
        assertEquals(ErrorCode.UNAUTHORIZED, rejectionCode(token("ADMIN", issuer = "someone-else")))
    }

    @Test
    fun rejectsWrongSignatureWithUnauthorized() {
        val otherKey = Keys.hmacShaKeyFor("another-secret-key-at-least-32-bytes!!".toByteArray(StandardCharsets.UTF_8))
        val forged = Jwts.builder().issuer(issuer).claim("role", "ADMIN").signWith(otherKey).compact()

        assertEquals(ErrorCode.UNAUTHORIZED, rejectionCode(forged))
    }
}
