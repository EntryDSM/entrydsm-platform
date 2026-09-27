package hs.kr.entrydsm.application.config

import hs.kr.entrydsm.application.application.port.`in`.ApplicationPort
import hs.kr.entrydsm.application.application.port.`in`.EvaluationPort
import hs.kr.entrydsm.application.application.port.`in`.MiddleSchoolPort
import hs.kr.entrydsm.application.application.port.out.ApplicantRepository
import hs.kr.entrydsm.application.application.port.out.ApplicantStatusEventOutbox
import hs.kr.entrydsm.application.application.port.out.ApplicationPeriodReader
import hs.kr.entrydsm.application.application.port.out.MiddleSchoolRepository
import hs.kr.entrydsm.application.application.service.ApplicationCommandService
import hs.kr.entrydsm.application.application.service.EvaluationCommandService
import hs.kr.entrydsm.application.application.service.MiddleSchoolQueryService
import hs.kr.entrydsm.application.domain.service.ScoreCalculator
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
class ApplicationUseCaseConfig {
    @Bean
    fun scoreCalculator(): ScoreCalculator = ScoreCalculator()

    @Bean
    fun applicationService(
        applicantRepository: ApplicantRepository,
        applicationPeriodReader: ApplicationPeriodReader,
        applicantStatusEventOutbox: ApplicantStatusEventOutbox,
    ): ApplicationPort = ApplicationCommandService(applicantRepository, applicationPeriodReader, applicantStatusEventOutbox)

    @Bean
    fun evaluationService(
        applicantRepository: ApplicantRepository,
        scoreCalculator: ScoreCalculator,
        applicationPeriodReader: ApplicationPeriodReader,
    ): EvaluationPort = EvaluationCommandService(applicantRepository, scoreCalculator, applicationPeriodReader)

    @Bean
    fun middleSchoolService(
        middleSchoolRepository: MiddleSchoolRepository,
    ): MiddleSchoolPort = MiddleSchoolQueryService(middleSchoolRepository)
}
