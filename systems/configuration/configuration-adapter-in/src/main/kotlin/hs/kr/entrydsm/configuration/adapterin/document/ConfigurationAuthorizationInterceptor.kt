package hs.kr.entrydsm.configuration.adapterin.document

import hs.kr.entrydsm.configuration.adapterin.schedule.ScheduleAccessDeniedException
import hs.kr.entrydsm.configuration.adapterin.schedule.ScheduleUnauthorizedException
import hs.kr.entrydsm.configuration.domain.document.Requester
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.context.annotation.Configuration
import org.springframework.stereotype.Component
import org.springframework.web.servlet.HandlerInterceptor
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

/** 컨트롤러가 `@RequestAttribute` 로 받는 요청자 속성 이름. */
const val REQUESTER_ATTRIBUTE = "documentRequester"

/**
 * 게이트웨이 헤더로 요청자를 만든다.
 * 역할별로 무엇을 적재·다운로드할 수 있는지는 FileCategory 권한표가 정한다.
 */
@Component
class ConfigurationAuthorizationInterceptor : HandlerInterceptor {
    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        val userId = request.getHeader("X-User-Id")?.toLongOrNull()
        val role = request.getHeader("X-User-Role")
        if (userId == null || role.isNullOrBlank()) {
            throw ScheduleUnauthorizedException()
        }
        val requesterRole = Requester.Role.entries.firstOrNull { it.name == role }
            ?: throw ScheduleAccessDeniedException()
        request.setAttribute(REQUESTER_ATTRIBUTE, Requester(userId, requesterRole))
        return true
    }
}

/**
 * 문서 경로에만 건다. 다른 모듈과 한 컨텍스트에 올라가므로 API 경로 전체에 걸면
 * 로그인·공지·모니터링 요청까지 이 인터셉터를 통과해야 한다.
 */
@Configuration(proxyBeanMethods = false)
class ConfigurationWebConfiguration(
    private val authorizationInterceptor: ConfigurationAuthorizationInterceptor,
) : WebMvcConfigurer {
    override fun addInterceptors(registry: InterceptorRegistry) {
        registry
            .addInterceptor(authorizationInterceptor)
            .addPathPatterns("/api/document/**")
    }
}
