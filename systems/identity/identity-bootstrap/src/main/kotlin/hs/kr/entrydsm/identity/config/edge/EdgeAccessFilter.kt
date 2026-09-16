package hs.kr.entrydsm.identity.config.edge

import hs.kr.entrydsm.identity.application.port.out.RefreshTokenStoreUnavailableException
import hs.kr.entrydsm.identity.config.security.AccessTokenAuthenticator
import hs.kr.entrydsm.identity.config.security.AccessTokenInvalidException
import hs.kr.entrydsm.identity.config.security.AuthenticatedAccount
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.dao.DataAccessException
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/**
 * 프로세스 바깥에서 들어온 요청을 정리한다. gateway 의 GatewayAccessGlobalFilter 를 옮겼다.
 *
 * 1. 클라이언트가 보낸 신뢰 헤더를 지우고 `X-Real-IP` 를 엣지가 계산한 값으로 채운다.
 * 2. 라우팅하던 경로가 아니면 404 로 끊는다.
 * 3. identity 이외 경로에 접근 토큰 쿠키가 있으면 in-process 로 검증해 신뢰 헤더를 채운다.
 *    각 모듈 인터셉터는 예전처럼 이 헤더만 보고 권한을 판단한다.
 */
@Component
class EdgeAccessFilter(
    private val authenticator: AccessTokenAuthenticator,
) : OncePerRequestFilter() {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val path = request.requestURI.removePrefix(request.contextPath.orEmpty())
        if (!EdgeContract.isRouted(path)) {
            EdgeErrorResponseWriter.write(request, response, EdgeError.ROUTE_NOT_FOUND)
            return
        }

        val clientIp = ClientIpResolver.resolve(request)
        val token = accessToken(request)
        if (EdgeContract.isIdentityPath(path) || token == null) {
            filterChain.doFilter(sanitized(request, clientIp), response)
            return
        }

        val account = try {
            authenticator.authenticate(token)
        } catch (exception: AccessTokenInvalidException) {
            EdgeErrorResponseWriter.write(request, response, EdgeError.AUTH_UNAUTHORIZED)
            return
        } catch (exception: RefreshTokenStoreUnavailableException) {
            logger.warn("token store unavailable while authenticating a request", exception)
            EdgeErrorResponseWriter.write(request, response, EdgeError.IDENTITY_UNAVAILABLE)
            return
        } catch (exception: DataAccessException) {
            logger.warn("account store unavailable while authenticating a request", exception)
            EdgeErrorResponseWriter.write(request, response, EdgeError.IDENTITY_UNAVAILABLE)
            return
        }

        filterChain.doFilter(sanitized(request, clientIp, account), response)
    }

    private fun accessToken(request: HttpServletRequest): String? =
        request.cookies
            ?.firstOrNull { it.name == EdgeContract.ACCESS_TOKEN_COOKIE }
            ?.value
            ?.takeIf { it.isNotBlank() }

    private fun sanitized(
        request: HttpServletRequest,
        clientIp: String,
        account: AuthenticatedAccount? = null,
    ): HttpServletRequest {
        val overrides = buildMap {
            put(EdgeContract.CLIENT_IP_HEADER, clientIp)
            account?.let {
                put(EdgeContract.USER_ID_HEADER, it.userId.toString())
                put(EdgeContract.USER_ROLE_HEADER, it.role.name)
                put(EdgeContract.LEGACY_USER_ID_HEADER, it.userId.toString())
                put(EdgeContract.SENSITIVE_AGREE_HEADER, it.isSensitiveAgree.toString())
            }
        }
        return TrustedHeaderRequestWrapper(
            request = request,
            overrides = overrides,
            removed = EdgeContract.UNTRUSTED_HEADERS.toSet(),
        )
    }
}
