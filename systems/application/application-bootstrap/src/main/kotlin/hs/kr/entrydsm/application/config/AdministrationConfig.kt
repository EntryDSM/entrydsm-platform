package hs.kr.entrydsm.application.config

import hs.kr.entrydsm.admin.domain.document.DocumentNaming
import java.time.Clock
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor

@Configuration(proxyBeanMethods = false)
@EnableAsync
@ConditionalOnProperty(name = ["application.administration.enabled"], havingValue = "true")
class AdministrationConfig(@Value("\${admin.storage.environment}") environment: String) {
    init { DocumentNaming.keyRoot(environment) }

    @Bean
    fun administrationClock(): Clock = Clock.systemUTC()

    @Bean("exportTaskExecutor")
    fun exportTaskExecutor() = ThreadPoolTaskExecutor().apply {
        corePoolSize = 1
        maxPoolSize = 1
        queueCapacity = 10
        setThreadNamePrefix("application-export-")
    }

}
