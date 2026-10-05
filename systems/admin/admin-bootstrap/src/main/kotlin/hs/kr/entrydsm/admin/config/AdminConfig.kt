package hs.kr.entrydsm.admin.config

import hs.kr.entrydsm.admin.adapterin.web.AdminAuthorizationInterceptor
import hs.kr.entrydsm.admin.adapterin.web.AdminEndpointPaths
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration(proxyBeanMethods = false)
class AdminConfig(private val adminAuthorizationInterceptor: AdminAuthorizationInterceptor) : WebMvcConfigurer {
    override fun addInterceptors(registry: InterceptorRegistry) {
        registry.addInterceptor(adminAuthorizationInterceptor).addPathPatterns("${AdminEndpointPaths.BASE}/**")
    }
}
