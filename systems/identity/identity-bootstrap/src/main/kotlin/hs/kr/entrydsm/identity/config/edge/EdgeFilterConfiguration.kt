package hs.kr.entrydsm.identity.config.edge

import hs.kr.entrydsm.identity.config.security.JwtFilter
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.Ordered

/**
 * 엣지 필터의 실행 순서를 정한다.
 *
 * trace id 와 본문 크기 제한은 보안 체인(-100)보다 앞에서 돌아야 모든 응답에 trace id 가 남고,
 * 큰 본문이 폼 파싱·멀티파트 처리로 들어가기 전에 끊긴다.
 */
@Configuration(proxyBeanMethods = false)
class EdgeFilterConfiguration {
    @Bean
    fun traceIdFilterRegistration(): FilterRegistrationBean<TraceIdFilter> =
        FilterRegistrationBean(TraceIdFilter()).apply {
            order = Ordered.HIGHEST_PRECEDENCE + TRACE_ID_ORDER_OFFSET
            addUrlPatterns("/*")
        }

    @Bean
    fun requestBodyLimitFilterRegistration(properties: EdgeProperties): FilterRegistrationBean<RequestBodyLimitFilter> =
        FilterRegistrationBean(RequestBodyLimitFilter(properties.request.maxBodyBytes)).apply {
            order = Ordered.HIGHEST_PRECEDENCE + REQUEST_LIMIT_ORDER_OFFSET
            addUrlPatterns("/*")
        }

    /** 보안 체인 안에서 CSRF 다음에 돌아야 한다. 컨테이너가 따로 등록하면 순서가 어긋난다. */
    @Bean
    fun edgeAccessFilterRegistration(filter: EdgeAccessFilter): FilterRegistrationBean<EdgeAccessFilter> =
        FilterRegistrationBean(filter).apply { isEnabled = false }

    @Bean
    fun jwtFilterRegistration(filter: JwtFilter): FilterRegistrationBean<JwtFilter> =
        FilterRegistrationBean(filter).apply { isEnabled = false }

    private companion object {
        const val TRACE_ID_ORDER_OFFSET = 2
        const val REQUEST_LIMIT_ORDER_OFFSET = 3
    }
}
