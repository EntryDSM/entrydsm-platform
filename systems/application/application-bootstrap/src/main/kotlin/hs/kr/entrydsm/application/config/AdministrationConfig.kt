package hs.kr.entrydsm.application.config

import hs.kr.entrydsm.admin.domain.document.DocumentNaming
import hs.kr.entrydsm.application.domain.service.DocumentPassCalculator
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

    /** 수동·자동 선발과 통계가 같은 설정 배수의 계산기를 사용한다. */
    @Bean
    fun documentPassCalculator(
        @Value("\${admin.screening.first-pass-multiplier:2}") multiplier: Double,
    ): DocumentPassCalculator = DocumentPassCalculator(multiplier)

    @Bean("exportTaskExecutor")
    fun exportTaskExecutor() = ThreadPoolTaskExecutor().apply {
        corePoolSize = 1
        maxPoolSize = 1
        queueCapacity = 10
        setThreadNamePrefix("application-export-")
    }

}
