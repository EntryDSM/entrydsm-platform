package hs.kr.entrydsm.admin.adapterin.web

import hs.kr.entrydsm.admin.domain.enum.ErrorCode
import hs.kr.entrydsm.admin.domain.exception.AdminDomainException
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.stereotype.Component
import org.springframework.web.servlet.HandlerInterceptor

const val USER_ID_HEADER = "X-User-Id"
const val USER_ROLE_HEADER = "X-User-Role"
private const val ADMIN_ROLE = "ADMIN"

/**
 * 관리자 권한을 확인합니다.
 *
 * 공통 규약 7항대로 인증은 Gateway 또는 Identity가 처리하고, 이 서비스는 그들이 넣어 준
 * 헤더만 신뢰합니다. 클라이언트가 직접 넣은 헤더가 그대로 들어오지 않도록 Gateway에서
 * 반드시 덮어써야 합니다.
 *
 * ponytail: Gateway가 없는 동안은 이 헤더가 유일한 관문이다. Gateway가 붙으면
 * 그쪽 JWT 검증으로 옮기고 여기서는 역할 확인만 남긴다.
 */
@Component
class AdminAuthorizationInterceptor : HandlerInterceptor {

    override fun preHandle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any,
    ): Boolean {
        val userId = request.getHeader(USER_ID_HEADER)
        val role = request.getHeader(USER_ROLE_HEADER)

        if (userId.isNullOrBlank() || role.isNullOrBlank()) {
            throw AdminDomainException(ErrorCode.AUTH_UNAUTHORIZED)
        }
        if (role != ADMIN_ROLE) {
            throw AdminDomainException(ErrorCode.ACCESS_DENIED)
        }

        return true
    }
}
