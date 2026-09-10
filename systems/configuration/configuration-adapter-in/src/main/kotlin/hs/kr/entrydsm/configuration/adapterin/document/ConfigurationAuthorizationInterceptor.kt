package hs.kr.entrydsm.configuration.adapterin.document

import hs.kr.entrydsm.configuration.adapterin.schedule.ScheduleAccessDeniedException
import hs.kr.entrydsm.configuration.adapterin.schedule.ScheduleUnauthorizedException
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.context.annotation.Configuration
import org.springframework.stereotype.Component
import org.springframework.web.servlet.HandlerInterceptor
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Component
class ConfigurationAuthorizationInterceptor : HandlerInterceptor {
    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        val role = request.getHeader("X-User-Role")
        if (request.getHeader("X-User-Id").isNullOrBlank() || role.isNullOrBlank()) {
            throw ScheduleUnauthorizedException()
        }
        if (role == "ADMIN") return true
        if (role != "STUDENT" || "${request.method} ${request.requestURI}" !in STUDENT_ENDPOINTS) {
            throw ScheduleAccessDeniedException()
        }
        return true
    }

    private companion object {
        val STUDENT_ENDPOINTS = setOf(
            "POST /api/document/v11/admission-ticket",
            "POST /api/document/v11/application",
            "GET /api/document/v11/admission-ticket/download",
            "GET /api/document/v11/application",
            "GET /api/document/v11/application/download",
            "GET /api/document/v11/guideline/download",
        )
    }
}

@Configuration(proxyBeanMethods = false)
class ConfigurationWebConfiguration(
    private val authorizationInterceptor: ConfigurationAuthorizationInterceptor,
) : WebMvcConfigurer {
    override fun addInterceptors(registry: InterceptorRegistry) {
        registry
            .addInterceptor(authorizationInterceptor)
            .addPathPatterns("/api/**")
            .excludePathPatterns("/api/schedule/**")
    }
}
