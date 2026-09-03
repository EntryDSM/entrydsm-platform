package hs.kr.entrydsm.admin.config

import hs.kr.entrydsm.admin.adapterin.web.AdminAuthorizationInterceptor
import hs.kr.entrydsm.admin.adapterin.web.AdminEndpointPaths
import hs.kr.entrydsm.admin.application.AdmissionQuotaProperties
import java.time.Clock
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.presigner.S3Presigner

/**
 * S3 클라이언트는 AWS 표준 설정 체계(`AWS_REGION`, `AWS_ENDPOINT_URL_S3`, 자격 증명 체인)를
 * 그대로 씁니다. 별도 프로퍼티로 감싸지 않아야 로컬 MinIO와 운영 배포가 같은 방식으로 동작합니다.
 */
@Configuration(proxyBeanMethods = false)
@EnableAsync
@EnableConfigurationProperties(AdmissionQuotaProperties::class)
class AdminConfig(
    private val adminAuthorizationInterceptor: AdminAuthorizationInterceptor,
) : WebMvcConfigurer {

    @Bean
    fun clock(): Clock = Clock.systemUTC()

    @Bean
    fun s3Client(): S3Client = S3Client.create()

    @Bean
    fun s3Presigner(): S3Presigner = S3Presigner.create()

    override fun addInterceptors(registry: InterceptorRegistry) {
        registry
            .addInterceptor(adminAuthorizationInterceptor)
            .addPathPatterns("${AdminEndpointPaths.BASE}/**")
    }
}
