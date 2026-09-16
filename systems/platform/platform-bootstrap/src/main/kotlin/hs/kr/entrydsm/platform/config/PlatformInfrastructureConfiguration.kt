package hs.kr.entrydsm.platform.config

import java.time.Clock
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.presigner.S3Presigner

/**
 * 여러 모듈이 같이 쓰는 인프라 빈을 한 번만 정의한다.
 *
 * 서비스가 나뉘어 있을 때는 admin·identity·observability 가 각자 `clock`을, admin·configuration 이 각자
 * `s3Client`·`s3Presigner`를 만들었다. 한 컨텍스트에서는 같은 이름의 빈이 기동을 막으므로 여기로 모았다.
 * 버킷처럼 모듈마다 다른 값은 모듈 설정이 각자 갖는다.
 */
@Configuration(proxyBeanMethods = false)
class PlatformInfrastructureConfiguration {

    @Bean
    fun clock(): Clock = Clock.systemUTC()

    /** 엔드포인트와 자격 증명은 AWS 표준 설정(`AWS_ENDPOINT_URL_S3`, 자격 증명 체인)을 그대로 따른다. */
    @Bean
    fun s3Client(@Value("\${aws.s3.region}") region: String): S3Client =
        S3Client.builder()
            .region(Region.of(region))
            .build()

    @Bean
    fun s3Presigner(@Value("\${aws.s3.region}") region: String): S3Presigner =
        S3Presigner.builder()
            .region(Region.of(region))
            .build()
}
