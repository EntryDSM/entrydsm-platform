package hs.kr.entrydsm.application.administration.adapterout

import hs.kr.entrydsm.admin.domain.command.EvaluateScreeningCommand
import hs.kr.entrydsm.admin.domain.enum.AdmissionType
import hs.kr.entrydsm.admin.domain.enum.Region
import hs.kr.entrydsm.admin.domain.model.Applicant
import hs.kr.entrydsm.admin.domain.model.ScreeningResult
import hs.kr.entrydsm.admin.domain.port.`in`.EvaluateFirstScreeningUseCase
import hs.kr.entrydsm.admin.domain.port.out.ApplicantRepository
import hs.kr.entrydsm.application.administration.adapterout.entity.ScheduleJpaEntity
import hs.kr.entrydsm.application.administration.adapterout.repository.ScheduleJpaRepository
import hs.kr.entrydsm.application.administration.application.FirstScreeningScheduler
import hs.kr.entrydsm.application.administration.application.ScreeningService
import java.lang.reflect.Proxy
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.persistence.autoconfigure.EntityScan
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.FilterType
import org.springframework.context.annotation.Import
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.junit4.SpringRunner
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@RunWith(SpringRunner::class)
@DataJpaTest
@org.springframework.test.context.ActiveProfiles("test")
@ContextConfiguration(classes = [SchedulePersistenceAdapterTest.Config::class])
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class SchedulePersistenceAdapterTest {
    @Autowired private lateinit var repository: ScheduleJpaRepository
    @Autowired private lateinit var adapter: SchedulePersistenceAdapter
    @Autowired private lateinit var scheduler: FirstScreeningScheduler
    @Autowired private lateinit var writer: ResultWriter
    @Autowired private lateinit var jdbc: JdbcTemplate
    @Autowired private lateinit var transactions: org.springframework.transaction.PlatformTransactionManager
    private val announcement = LocalDateTime.of(2026, 10, 30, 10, 0)

    @Before fun reset() {
        writer.fail = false
        jdbc.execute("CREATE TABLE IF NOT EXISTS first_screening_test_result (id BIGINT PRIMARY KEY, status VARCHAR(20) NOT NULL)")
        jdbc.update("DELETE FROM first_screening_test_result")
        repository.deleteAll()
        repository.saveAndFlush(ScheduleJpaEntity(title = "1차 합격 발표", startAt = announcement, endAt = announcement.plusDays(1)))
    }

    @Test fun `실제 저장소는 발표 전 선점을 거부하고 처리 시각을 영속 저장한다`() {
        assertFalse(adapter.claimFirstScreening("1차 합격 발표", announcement.minusNanos(1)))
        assertFalse(adapter.claimFirstScreening("1차 발표", announcement))
        assertTrue(adapter.claimFirstScreening("1차 합격 발표", announcement))
        assertEquals(announcement, repository.findByTitle("1차 합격 발표")!!.firstScreeningProcessedAt)
        assertFalse(adapter.claimFirstScreening("1차 합격 발표", announcement.plusHours(1)))
        assertFalse(org.springframework.transaction.support.TransactionTemplate(transactions).execute {
            SchedulePersistenceAdapter(repository).claimFirstScreening("1차 합격 발표", announcement.plusDays(1))
        }!!)
    }

    @Test fun `선점 이전에 읽은 일정을 수정 저장해도 완료 표시가 지워지지 않는다`() {
        val stale = adapter.findByTitle("1차 합격 발표")!!
        assertNull(stale.firstScreeningProcessedAt)
        scheduler.evaluateDue()
        adapter.save(stale.copy(endAt = announcement.plusDays(2)))
        assertEquals(announcement, adapter.findByTitle("1차 합격 발표")!!.firstScreeningProcessedAt)
        scheduler.evaluateDue()
        assertEquals(129L, resultCount())
    }

    @Test fun `자동 산출 저장 실패는 일정 선점과 결과 저장을 함께 롤백하고 재시도한다`() {
        writer.fail = true
        assertThrows(IllegalStateException::class.java) { scheduler.evaluateDue() }
        assertNull(repository.findByTitle("1차 합격 발표")!!.firstScreeningProcessedAt)
        assertEquals(0L, resultCount())
        writer.fail = false
        scheduler.evaluateDue()
        assertEquals(announcement, repository.findByTitle("1차 합격 발표")!!.firstScreeningProcessedAt)
        assertEquals(129L, resultCount())
        assertEquals((1L..104L).toList(), jdbc.queryForList("SELECT id FROM first_screening_test_result WHERE status = 'FIRST_PASS' ORDER BY id", Long::class.java))
        assertEquals((105L..129L).toList(), jdbc.queryForList("SELECT id FROM first_screening_test_result WHERE status = 'FIRST_FAIL' ORDER BY id", Long::class.java))
        scheduler.evaluateDue()
        assertEquals(129L, resultCount())
    }

    @Test fun `동시 자동 실행은 한 번만 선점하고 결과를 저장한다`() {
        val executor = Executors.newFixedThreadPool(2)
        val start = CountDownLatch(1)
        try {
            val tasks = (1..2).map { executor.submit { start.await(); scheduler.evaluateDue() } }
            start.countDown()
            tasks.forEach { it.get(20, TimeUnit.SECONDS) }
            assertEquals(129L, resultCount())
            assertNotNull(repository.findByTitle("1차 합격 발표")!!.firstScreeningProcessedAt)
        } finally { executor.shutdownNow() }
    }

    private fun resultCount() = jdbc.queryForObject("SELECT COUNT(*) FROM first_screening_test_result", Long::class.java)

    class ResultWriter(jdbc: JdbcTemplate, clock: Clock) : EvaluateFirstScreeningUseCase {
        var fail = false
        private val applicants = (1L..129L).map { Applicant(id = it, admissionType = AdmissionType.GENERAL,
            region = Region.NATIONWIDE, totalScore = 1000.0 - it, submittedAt = Instant.EPOCH,
            isArrived = false, examineeNumber = null) }
        private val repository = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(ApplicantRepository::class.java)) { _, method, args ->
            when (method.name) {
                "findAll" -> applicants
                "saveAll" -> (args[0] as List<Applicant>).also { saved ->
                    saved.forEach {
                        assertEquals(Instant.parse("2026-10-30T01:00:00Z"), it.updatedAt)
                        jdbc.update("INSERT INTO first_screening_test_result (id, status) VALUES (?, ?)", it.id, it.status.name)
                    }
                }
                else -> error("unexpected call: ${method.name}")
            }
        } as ApplicantRepository
        private val service = ScreeningService(repository, clock)
        override fun evaluateFirst(command: EvaluateScreeningCommand): ScreeningResult {
            assertFalse(command.dryRun)
            val result = service.evaluateFirst(command)
            if (fail) error("결과 저장 실패")
            return result
        }
    }

    @SpringBootConfiguration(proxyBeanMethods = false)
    @EntityScan(basePackageClasses = [ScheduleJpaEntity::class])
    @EnableJpaRepositories(basePackageClasses = [ScheduleJpaRepository::class],
        includeFilters = [ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = [ScheduleJpaRepository::class])])
    @Import(SchedulePersistenceAdapter::class, FirstScreeningScheduler::class)
    class Config {
        @Bean fun clock(): Clock = Clock.fixed(Instant.parse("2026-10-30T01:00:00Z"), ZoneOffset.UTC)
        @Bean fun screening(jdbc: JdbcTemplate, clock: Clock) = ResultWriter(jdbc, clock)
    }
}
