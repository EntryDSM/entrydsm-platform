package hs.kr.entrydsm.identity.config

import org.springframework.boot.persistence.autoconfigure.EntityScan
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.FullyQualifiedAnnotationBeanNameGenerator
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.scheduling.annotation.EnableScheduling

/**
 * identity 모듈을 platform 컨텍스트에 올리는 지점이다.
 *
 * 스캔은 identity 패키지로 한정하고 빈 이름은 FQCN 으로 만든다.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ComponentScan(
    basePackages = ["hs.kr.entrydsm.identity"],
    nameGenerator = FullyQualifiedAnnotationBeanNameGenerator::class,
)
@EntityScan(basePackages = ["hs.kr.entrydsm.identity"])
@EnableJpaRepositories(
    basePackages = ["hs.kr.entrydsm.identity"],
    nameGenerator = FullyQualifiedAnnotationBeanNameGenerator::class,
)
class IdentityModuleConfiguration
