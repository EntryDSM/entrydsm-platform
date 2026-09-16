package hs.kr.entrydsm.application.config

import org.springframework.boot.persistence.autoconfigure.EntityScan
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.FullyQualifiedAnnotationBeanNameGenerator
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.scheduling.annotation.EnableScheduling

/**
 * application 모듈을 platform 컨텍스트에 올리는 지점이다.
 *
 * 스캔은 application 패키지로 한정하고 빈 이름은 FQCN 으로 만든다.
 * 상태 이벤트 릴레이가 스케줄로 돌기 때문에 스케줄링을 켠다.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ComponentScan(
    basePackages = ["hs.kr.entrydsm.application"],
    nameGenerator = FullyQualifiedAnnotationBeanNameGenerator::class,
)
@EntityScan(basePackages = ["hs.kr.entrydsm.application"])
@EnableJpaRepositories(
    basePackages = ["hs.kr.entrydsm.application"],
    nameGenerator = FullyQualifiedAnnotationBeanNameGenerator::class,
)
class ApplicationModuleConfiguration
