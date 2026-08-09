package hs.kr.entrydsm.observability.adapterin.web.security

import hs.kr.entrydsm.observability.domain.enum.ErrorCode
import hs.kr.entrydsm.observability.domain.exception.MonitorDomainException
import io.jsonwebtoken.JwtException
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import java.nio.charset.StandardCharsets
import org.springframework.stereotype.Component
import org.springframework.web.servlet.HandlerInterceptor

/**
 * ponytail: 이 저장소엔 아직 Spring Security/JWT 발급 체계가 병합되어 있지 않아,
 * 전체 Security 스택 대신 Bearer 토큰 서명·발급자·role 클레임만 확인하는 경량 인터셉터로 둔다.
 * identity의 정식 인증이 병합되면 같은 시크릿을 공유하도록 설정만 맞추면 된다.
 */
@Component
class JwtAuthInterceptor(
    private val properties: JwtAuthProperties,
) : HandlerInterceptor {
    private val key by lazy { Keys.hmacShaKeyFor(properties.secret.toByteArray(StandardCharsets.UTF_8)) }

    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        val token = bearerToken(request) ?: throw MonitorDomainException(ErrorCode.UNAUTHORIZED)
        val claims = try {
            Jwts.parser()
                .verifyWith(key)
                .requireIssuer(properties.issuer)
                .build()
                .parseSignedClaims(token)
                .payload
        } catch (exception: JwtException) {
            throw MonitorDomainException(ErrorCode.UNAUTHORIZED)
        } catch (exception: IllegalArgumentException) {
            throw MonitorDomainException(ErrorCode.UNAUTHORIZED)
        }
        if (claims["role"] as? String != ADMIN_ROLE) {
            throw MonitorDomainException(ErrorCode.FORBIDDEN)
        }
        return true
    }

    private fun bearerToken(request: HttpServletRequest): String? {
        val header = request.getHeader("Authorization") ?: return null
        if (!header.startsWith(BEARER_PREFIX)) return null
        return header.removePrefix(BEARER_PREFIX).trim().takeIf { it.isNotEmpty() }
    }

    companion object {
        private const val BEARER_PREFIX = "Bearer "
        private const val ADMIN_ROLE = "ADMIN"
    }
}
