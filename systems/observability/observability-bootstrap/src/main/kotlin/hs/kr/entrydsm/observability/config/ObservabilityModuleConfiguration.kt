package hs.kr.entrydsm.observability.config

import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.FullyQualifiedAnnotationBeanNameGenerator
import org.springframework.scheduling.annotation.EnableScheduling

/**
 * observability 모듈을 platform 컨텍스트에 올리는 지점이다.
 *
 * 스캔은 observability 패키지로 한정하고 빈 이름은 FQCN 으로 만든다. 이 모듈은 DB 를 쓰지 않는다.
 * SSE 브로드캐스트와 동시 접속 샘플링이 스케줄로 돌기 때문에 스케줄링을 켠다.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ComponentScan(
    basePackages = ["hs.kr.entrydsm.observability"],
    nameGenerator = FullyQualifiedAnnotationBeanNameGenerator::class,
)
class ObservabilityModuleConfiguration
