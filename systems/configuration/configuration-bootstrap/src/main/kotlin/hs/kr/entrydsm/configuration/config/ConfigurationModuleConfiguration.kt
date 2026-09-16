package hs.kr.entrydsm.configuration.config

import org.springframework.boot.persistence.autoconfigure.EntityScan
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.FullyQualifiedAnnotationBeanNameGenerator
import org.springframework.data.jpa.repository.config.EnableJpaRepositories

/**
 * configuration 모듈을 platform 컨텍스트에 올리는 지점이다.
 *
 * 스캔은 configuration 패키지로 한정하고 빈 이름은 FQCN 으로 만든다.
 */
@Configuration(proxyBeanMethods = false)
@ComponentScan(
    basePackages = ["hs.kr.entrydsm.configuration"],
    nameGenerator = FullyQualifiedAnnotationBeanNameGenerator::class,
)
@EntityScan(basePackages = ["hs.kr.entrydsm.configuration"])
@EnableJpaRepositories(
    basePackages = ["hs.kr.entrydsm.configuration"],
    nameGenerator = FullyQualifiedAnnotationBeanNameGenerator::class,
)
class ConfigurationModuleConfiguration
