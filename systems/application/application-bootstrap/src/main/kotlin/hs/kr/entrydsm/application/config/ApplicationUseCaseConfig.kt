package hs.kr.entrydsm.application.config

import hs.kr.entrydsm.application.application.port.`in`.ApplicationPort
import hs.kr.entrydsm.application.application.port.`in`.EvaluationPort
import hs.kr.entrydsm.application.application.port.out.ApplicantRepository
import hs.kr.entrydsm.application.application.service.ApplicationCommandService
import hs.kr.entrydsm.application.application.service.EvaluationCommandService
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
class ApplicationUseCaseConfig {
    @Bean
    fun applicationService(
        applicantRepository: ApplicantRepository,
    ): ApplicationPort = ApplicationCommandService(applicantRepository)

    @Bean
    fun evaluationService(
        applicantRepository: ApplicantRepository,
    ): EvaluationPort = EvaluationCommandService(applicantRepository)
}
