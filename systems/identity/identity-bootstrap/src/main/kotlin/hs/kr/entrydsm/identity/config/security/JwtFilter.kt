package hs.kr.entrydsm.identity.config.security

import hs.kr.entrydsm.identity.application.port.out.RefreshTokenStoreUnavailableException
import hs.kr.entrydsm.identity.application.security.AuthenticatedUser
import hs.kr.entrydsm.identity.application.web.AuthEndpointPaths
import hs.kr.entrydsm.identity.config.edge.EdgeContract
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/**
 * identity 경로의 인증을 담당한다.
 *
 * 다른 모듈 경로는 엣지가 쿠키로 인증해 신뢰 헤더를 채우므로 이 필터를 태우지 않는다.
 * 그래야 Bearer 헤더가 다른 모듈에서도 통하거나 오류 형식이 뒤섞이는 일이 없다.
 */
@Component
class JwtFilter(
    private val authenticator: AccessTokenAuthenticator,
    private val authenticationEntryPoint: AuthenticationEntryPoint,
) : OncePerRequestFilter() {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun shouldNotFilter(request: HttpServletRequest): Boolean =
        !EdgeContract.isIdentityPath(request.path())

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
            val account = authenticator.authenticate(token)
            SecurityContextHolder.getContext().authentication =
                UsernamePasswordAuthenticationToken(AuthenticatedUser(account.userId), null, emptyList())
            filterChain.doFilter(request, response)
        } catch (exception: AccessTokenInvalidException) {
            SecurityContextHolder.clearContext()
            authenticationEntryPoint.commence(request, response, BadCredentialsException("Invalid JWT", exception))
        } catch (exception: RefreshTokenStoreUnavailableException) {
            SecurityContextHolder.clearContext()
            logger.warn("Redis unavailable while validating access token", exception)
            RedisUnavailableResponseWriter.write(response)
        }
    }

    private fun isPublicAuthPath(request: HttpServletRequest): Boolean =
        AuthEndpointPaths.PUBLIC.contains(request.path())

    private fun HttpServletRequest.path(): String = requestURI.removePrefix(contextPath.orEmpty())

    private fun resolveToken(request: HttpServletRequest): String? {
        val authorization = request.getHeader("Authorization")
        if (authorization?.startsWith(BEARER_PREFIX) == true) {
            return authorization.removePrefix(BEARER_PREFIX).takeIf { it.isNotBlank() }
        }
        return request.cookies
            ?.firstOrNull { it.name == EdgeContract.ACCESS_TOKEN_COOKIE }
            ?.value
            ?.takeIf { it.isNotBlank() }
    }

    private companion object {
        const val BEARER_PREFIX = "Bearer "
    }
}
