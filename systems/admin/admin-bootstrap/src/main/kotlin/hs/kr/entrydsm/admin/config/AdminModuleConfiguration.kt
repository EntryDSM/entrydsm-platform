package hs.kr.entrydsm.admin.config

import org.springframework.boot.persistence.autoconfigure.EntityScan
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.FullyQualifiedAnnotationBeanNameGenerator
import org.springframework.data.jpa.repository.config.EnableJpaRepositories

/**
 * admin 모듈을 platform 컨텍스트에 올리는 지점이다.
 *
 * 스캔은 admin 패키지로 한정하고 빈 이름은 FQCN 으로 만든다. 다른 모듈에 같은 이름의 클래스
 * (`GlobalExceptionHandler`, `ApplicantPersistenceAdapter` 등)가 있어도 부딪히지 않는다.
 */
@Configuration(proxyBeanMethods = false)
@ComponentScan(
    basePackages = ["hs.kr.entrydsm.admin"],
    nameGenerator = FullyQualifiedAnnotationBeanNameGenerator::class,
)
@EntityScan(basePackages = ["hs.kr.entrydsm.admin"])
@EnableJpaRepositories(
    basePackages = ["hs.kr.entrydsm.admin"],
    nameGenerator = FullyQualifiedAnnotationBeanNameGenerator::class,
)
class AdminModuleConfiguration
