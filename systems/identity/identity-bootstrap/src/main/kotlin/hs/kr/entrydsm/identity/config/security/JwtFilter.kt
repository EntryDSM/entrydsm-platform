package hs.kr.entrydsm.identity.config.security

import io.jsonwebtoken.JwtException
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import hs.kr.entrydsm.identity.application.security.AuthenticatedUser
import hs.kr.entrydsm.identity.application.port.out.AccountQueryPort
import hs.kr.entrydsm.identity.application.port.out.RefreshTokenStoreUnavailableException
import hs.kr.entrydsm.identity.application.port.out.RefreshTokenRevocationStore
import hs.kr.entrydsm.identity.application.security.jwt.JwtTokenGenerator
import hs.kr.entrydsm.identity.domain.enum.AccountStatus
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import java.nio.charset.StandardCharsets
import java.time.Clock
import java.time.Instant
import java.util.Date
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class JwtFilter(
    private val jwtProperties: JwtProperties,
    private val clock: Clock,
    private val authenticationEntryPoint: AuthenticationEntryPoint,
    private val accountQueryPort: AccountQueryPort,
    private val refreshTokenRevocationStore: RefreshTokenRevocationStore,
) : OncePerRequestFilter() {
    private val secretBytes = jwtProperties.secret.toByteArray(StandardCharsets.UTF_8)
    private val signingKey = Keys.hmacShaKeyFor(secretBytes)
    private val jwtParser = Jwts.parser()
        .verifyWith(signingKey)
        .clock(io.jsonwebtoken.Clock { Date.from(Instant.now(clock)) })
        .build()

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        if (isPublicAuthPath(request)) {
            filterChain.doFilter(request, response)
            return
        }

        val token = resolveToken(request)
        if (token == null) {
            filterChain.doFilter(request, response)
            return
        }

        try {
            val principal = verify(token)
            SecurityContextHolder.getContext().authentication =
                UsernamePasswordAuthenticationToken(principal, null, emptyList())
            filterChain.doFilter(request, response)
        } catch (exception: JwtValidationException) {
            SecurityContextHolder.clearContext()
            authenticationEntryPoint.commence(
                request,
                response,
                BadCredentialsException("Invalid JWT", exception),
            )
        } catch (exception: RefreshTokenStoreUnavailableException) {
            SecurityContextHolder.clearContext()
            response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE)
        }
    }

    private fun isPublicAuthPath(request: HttpServletRequest): Boolean {
        val path = request.requestURI.removePrefix(request.contextPath.orEmpty())
        return PUBLIC_AUTH_PATHS.contains(path)
    }

    private fun resolveToken(request: HttpServletRequest): String? {
        val authorization = request.getHeader("Authorization")
        if (authorization?.startsWith(BEARER_PREFIX) == true) {
            return authorization.removePrefix(BEARER_PREFIX).takeIf { it.isNotBlank() }
        }
        return request.cookies
            ?.firstOrNull { it.name == ACCESS_TOKEN_COOKIE }
            ?.value
            ?.takeIf { it.isNotBlank() }
    }

    private fun verify(token: String): AuthenticatedUser = try {
        val signedJwt = jwtParser.parseSignedClaims(token)
        if (signedJwt.header.algorithm != Jwts.SIG.HS256.id) throw JwtValidationException()

        val claims = signedJwt.payload
        if (claims.issuer != jwtProperties.issuer) throw JwtValidationException()
        if (claims[JwtTokenGenerator.TOKEN_TYPE_CLAIM] as? String != ACCESS_TOKEN_TYPE) {
            throw JwtValidationException()
        }

        val subject = claims.subject?.takeIf { it.isNotBlank() } ?: throw JwtValidationException()
        val userId = subject
            .removePrefix(USER_PRINCIPAL_PREFIX)
            .takeIf { subject.startsWith(USER_PRINCIPAL_PREFIX) }
            ?.toLongOrNull()
            ?.takeIf { it > 0 }
            ?: throw JwtValidationException()
        val expiration = claims.expiration?.toInstant() ?: throw JwtValidationException()
        if (!expiration.isAfter(Instant.now(clock))) throw JwtValidationException()
        val tokenVersion = (claims[JwtTokenGenerator.TOKEN_VERSION_CLAIM] as? Number)
            ?.toLong()
            ?.takeIf { it >= JwtTokenGenerator.INITIAL_TOKEN_VERSION }
            ?: throw JwtValidationException()
        if (refreshTokenRevocationStore.currentVersion(userId) != tokenVersion) {
            throw JwtValidationException()
        }
        val account = accountQueryPort.findByUserId(userId) ?: throw JwtValidationException()
        if (account.status != AccountStatus.ACTIVE) throw JwtValidationException()
        AuthenticatedUser(userId)
    } catch (exception: JwtValidationException) {
        throw exception
    } catch (exception: JwtException) {
        throw JwtValidationException(exception)
    } catch (exception: IllegalArgumentException) {
        throw JwtValidationException(exception)
    }

    private class JwtValidationException(cause: Throwable? = null) : RuntimeException(cause)

    companion object {
        private const val ACCESS_TOKEN_COOKIE = "access_token"
        private const val ACCESS_TOKEN_TYPE = "access"
        private const val BEARER_PREFIX = "Bearer "
        private const val USER_PRINCIPAL_PREFIX = "user_"
        private val PUBLIC_AUTH_PATHS = setOf(
            "/api/identity/v11/auth/signup",
            "/api/identity/v11/auth/login",
            "/api/identity/v11/auth/token",
            "/api/identity/v11/auth/password-reset",
        )
    }
}
