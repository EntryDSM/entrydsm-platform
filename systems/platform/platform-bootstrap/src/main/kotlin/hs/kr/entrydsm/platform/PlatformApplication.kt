package hs.kr.entrydsm.platform

import hs.kr.entrydsm.admin.config.AdminModuleConfiguration
import hs.kr.entrydsm.application.config.ApplicationModuleConfiguration
import hs.kr.entrydsm.configuration.config.ConfigurationModuleConfiguration
import hs.kr.entrydsm.identity.config.IdentityModuleConfiguration
import hs.kr.entrydsm.notification.config.NotificationModuleConfiguration
import hs.kr.entrydsm.observability.config.ObservabilityModuleConfiguration
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Import

/**
 * 모든 모듈을 하나의 Spring 컨텍스트로 조립하는 유일한 진입점이다.
 *
 * 이 클래스는 platform 패키지만 스캔한다. 모듈은 각자의 `*ModuleConfiguration`이 자기 패키지만 스캔하므로,
 * 이 프로세스에 무엇이 올라가는지는 아래 목록이 전부다.
 */
@SpringBootApplication
@Import(
    IdentityModuleConfiguration::class,
    ApplicationModuleConfiguration::class,
    ConfigurationModuleConfiguration::class,
    NotificationModuleConfiguration::class,
    AdminModuleConfiguration::class,
    ObservabilityModuleConfiguration::class,
)
class PlatformApplication

@Suppress("SpreadOperator")
fun main(args: Array<String>) {
    runApplication<PlatformApplication>(*args)
}
