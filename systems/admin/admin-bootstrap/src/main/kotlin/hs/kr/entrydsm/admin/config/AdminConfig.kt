package hs.kr.entrydsm.admin.config

import hs.kr.entrydsm.admin.adapterin.web.AdminAuthorizationInterceptor
import hs.kr.entrydsm.admin.adapterin.web.AdminEndpointPaths
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

/**
 * `Clock`과 S3 클라이언트는 여러 모듈이 같이 쓰므로 platform 이 정의한다.
 * 이 설정은 admin 전용 인터셉터와 비동기 내보내기 실행만 담당한다.
 */
@Configuration(proxyBeanMethods = false)
@EnableAsync
class AdminConfig(
    private val adminAuthorizationInterceptor: AdminAuthorizationInterceptor,
) : WebMvcConfigurer {

    override fun addInterceptors(registry: InterceptorRegistry) {
        registry
            .addInterceptor(adminAuthorizationInterceptor)
            .addPathPatterns("${AdminEndpointPaths.BASE}/**")
    }
}
