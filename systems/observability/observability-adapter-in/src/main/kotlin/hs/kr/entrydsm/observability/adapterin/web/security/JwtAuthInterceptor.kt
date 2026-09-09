package hs.kr.entrydsm.observability.adapterin.web.security

import hs.kr.entrydsm.observability.domain.enum.ErrorCode
import hs.kr.entrydsm.observability.domain.exception.MonitorDomainException
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.stereotype.Component
import org.springframework.web.servlet.HandlerInterceptor

@Component
class JwtAuthInterceptor : HandlerInterceptor {
    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        if (request.getHeader(USER_ID_HEADER).isNullOrBlank()) {
            throw MonitorDomainException(ErrorCode.UNAUTHORIZED)
        }
        if (request.getHeader(USER_ROLE_HEADER) != MONITOR_ROLE) {
            throw MonitorDomainException(ErrorCode.FORBIDDEN)
        }
        return true
    }

    companion object {
        private const val USER_ID_HEADER = "X-User-Id"
        private const val USER_ROLE_HEADER = "X-User-Role"
        private const val MONITOR_ROLE = "MONITOR"
    }
}
