package hs.kr.entrydsm.application.adapterin.web

import hs.kr.entrydsm.application.application.exception.ApplicationAccessDeniedException
import hs.kr.entrydsm.application.application.exception.AuthenticationRequiredException
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.context.annotation.Configuration
import org.springframework.stereotype.Component
import org.springframework.web.servlet.HandlerInterceptor
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Component
class ApplicationAuthorizationInterceptor : HandlerInterceptor {
    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        if (request.getHeader("X-User-Id").isNullOrBlank()) throw AuthenticationRequiredException()
        if (request.getHeader("X-User-Role") != "STUDENT") throw ApplicationAccessDeniedException()
        return true
    }
}

@Configuration(proxyBeanMethods = false)
class ApplicationWebConfiguration(
    private val authorizationInterceptor: ApplicationAuthorizationInterceptor,
) : WebMvcConfigurer {
    override fun addInterceptors(registry: InterceptorRegistry) {
        registry.addInterceptor(authorizationInterceptor).addPathPatterns("/api/application/**")
    }
}
