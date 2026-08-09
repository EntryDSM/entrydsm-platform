package hs.kr.entrydsm.observability.adapterin.web.security

import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
class WebMvcConfig(
    private val jwtAuthInterceptor: JwtAuthInterceptor,
) : WebMvcConfigurer {
    override fun addInterceptors(registry: InterceptorRegistry) {
        registry.addInterceptor(jwtAuthInterceptor)
            .addPathPatterns("/api/monitor/v11/**")
            .excludePathPatterns(
                "/api/monitor/v11/collect/session",
                "/api/monitor/v11/collect/client-log",
            )
    }
}
