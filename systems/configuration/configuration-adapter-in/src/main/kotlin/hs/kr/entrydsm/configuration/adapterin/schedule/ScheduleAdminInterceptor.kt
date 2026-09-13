package hs.kr.entrydsm.configuration.adapterin.schedule

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.stereotype.Component
import org.springframework.web.servlet.HandlerInterceptor
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

private const val USER_ID_HEADER = "X-User-Id"
private const val USER_ROLE_HEADER = "X-User-Role"
private const val ADMIN_ROLE = "ADMIN"
private const val SCHEDULE_PATH = "/api/schedule/**"

class ScheduleUnauthorizedException : RuntimeException("인증이 필요합니다.")

class ScheduleAccessDeniedException : RuntimeException("관리자 권한이 없습니다.")

@Component
class ScheduleAdminInterceptor : HandlerInterceptor {
    override fun preHandle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any,
    ): Boolean {
        if (request.method == HttpMethod.GET.name()) return true

        val userId = request.getHeader(USER_ID_HEADER)
        val role = request.getHeader(USER_ROLE_HEADER)
        if (userId.isNullOrBlank() || role.isNullOrBlank()) throw ScheduleUnauthorizedException()
        if (role != ADMIN_ROLE) throw ScheduleAccessDeniedException()
        return true
    }
}

@Configuration(proxyBeanMethods = false)
class ScheduleWebConfiguration(
    private val scheduleAdminInterceptor: ScheduleAdminInterceptor,
) : WebMvcConfigurer {
    override fun addInterceptors(registry: InterceptorRegistry) {
        registry.addInterceptor(scheduleAdminInterceptor).addPathPatterns(SCHEDULE_PATH)
    }
}
