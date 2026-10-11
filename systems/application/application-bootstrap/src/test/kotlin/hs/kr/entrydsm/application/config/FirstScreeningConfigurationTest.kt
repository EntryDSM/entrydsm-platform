package hs.kr.entrydsm.application.config

import hs.kr.entrydsm.admin.domain.command.EvaluateScreeningCommand
import hs.kr.entrydsm.admin.domain.enum.AdmissionType
import hs.kr.entrydsm.admin.domain.enum.ApplicantStatus
import hs.kr.entrydsm.admin.domain.enum.StatisticsMetric
import hs.kr.entrydsm.admin.domain.model.Applicant
import hs.kr.entrydsm.admin.domain.port.out.ApplicantRepository
import hs.kr.entrydsm.application.administration.application.FirstScreeningScheduler
import hs.kr.entrydsm.application.administration.application.ScreeningService
import hs.kr.entrydsm.application.administration.application.StatisticsService
import hs.kr.entrydsm.application.administration.domain.schedule.Schedule
import hs.kr.entrydsm.application.administration.domain.schedule.port.out.ScheduleRepository
import java.lang.reflect.Proxy
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Test
import org.springframework.boot.env.YamlPropertySourceLoader
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.core.env.MapPropertySource
import org.springframework.core.io.ClassPathResource

class FirstScreeningConfigurationTest {
    @Test
    fun `환경변수 미설정은 기본 2배수를 수동 자동 통계에 주입한다`() {
        verify(multiplier = null, passCount = 104, generalQuota = 64, socialQuota = 4, meisterQuota = 20, competition = 1.25)
    }

    @Test
    fun `환경변수 1점5배수를 수동 자동 통계에 동일하게 주입한다`() {
        verify(multiplier = "1.5", passCount = 78, generalQuota = 48, socialQuota = 3, meisterQuota = 15, competition = 1.67)
    }

    private fun verify(multiplier: String?, passCount: Int, generalQuota: Int, socialQuota: Int, meisterQuota: Int, competition: Double) {
        val applicants = (1L..130L).map { Applicant(id = it, admissionType = AdmissionType.GENERAL, totalScore = 1000.0 - it) }
        val saved = mutableListOf<Applicant>()
        val repository = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(ApplicantRepository::class.java)) { _, method, args ->
            when (method.name) {
                "findAll" -> applicants
                "saveAll" -> (args[0] as List<Applicant>).also(saved::addAll)
                else -> error("unexpected call: ${method.name}")
            }
        } as ApplicantRepository
        val schedules = object : ScheduleRepository {
            override fun claimFirstScreening(title: String, now: LocalDateTime) = true
            override fun findByTitle(title: String): Schedule? = error("unexpected query")
            override fun findByStartAtBetween(startAt: LocalDateTime, endAt: LocalDateTime): List<Schedule> = error("unexpected query")
            override fun save(schedule: Schedule): Schedule = error("unexpected save")
        }
        AnnotationConfigApplicationContext().use { context ->
            context.environment.propertySources.remove("systemEnvironment")
            context.environment.propertySources.remove("systemProperties")
            val overrides = mutableMapOf<String, Any>("application.administration.enabled" to "true")
            if (multiplier != null) overrides["FIRST_PASS_MULTIPLIER"] = multiplier
            context.environment.propertySources.addFirst(MapPropertySource("screening-test", overrides))
            YamlPropertySourceLoader().load("application", ClassPathResource("application.yaml")).forEach {
                context.environment.propertySources.addLast(it)
            }
            context.beanFactory.registerSingleton("applicantRepository", repository)
            context.beanFactory.registerSingleton("scheduleRepository", schedules)
            context.register(AdministrationConfig::class.java, ScreeningService::class.java, StatisticsService::class.java, FirstScreeningScheduler::class.java)
            context.refresh()

            val screening = context.getBean(ScreeningService::class.java)
            assertEquals(passCount, screening.evaluateFirst(EvaluateScreeningCommand()).passCount)
            assertEquals((1L..passCount.toLong()).toList(), saved.filter { it.status == ApplicantStatus.FIRST_PASS }.map { it.id })
            saved.clear()
            context.getBean(FirstScreeningScheduler::class.java).evaluateDue()
            assertEquals((1L..passCount.toLong()).toList(), saved.filter { it.status == ApplicantStatus.FIRST_PASS }.map { it.id })

            val statistics = context.getBean(StatisticsService::class.java).collect(setOf(StatisticsMetric.FIRST_PASS_QUOTA, StatisticsMetric.COMPETITION_RATE))
            assertEquals(mapOf(AdmissionType.GENERAL to generalQuota, AdmissionType.SOCIAL to socialQuota, AdmissionType.MEISTER to meisterQuota), statistics.firstPassQuota)
            assertEquals(mapOf(AdmissionType.GENERAL to competition, AdmissionType.SOCIAL to 0.0, AdmissionType.MEISTER to 0.0), statistics.competitionRate)
        }
    }
}
