package hs.kr.entrydsm.application.administration.application

import hs.kr.entrydsm.admin.domain.command.EvaluateScreeningCommand
import hs.kr.entrydsm.admin.domain.enum.AdmissionType
import hs.kr.entrydsm.admin.domain.enum.ApplicantStatus
import hs.kr.entrydsm.admin.domain.enum.Region
import hs.kr.entrydsm.admin.domain.model.Applicant
import hs.kr.entrydsm.admin.domain.port.out.ApplicantRepository
import java.lang.reflect.Proxy
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.*
import org.junit.Test

class ScreeningServiceTest {
    @Test
    fun `실제 수동 산출은 대전 우선과 모든 전형의 후순위를 저장한다`() {
        val fixture = Fixture(mixedApplicants())
        val result = fixture.service().evaluateFirst(EvaluateScreeningCommand())
        assertEquals(((1L..56L) + (81L..112L) + (121L..160L)).toSet(), fixture.passedIds())
        assertEquals(128, result.passCount)
        assertEquals(32, result.failCount)
        assertEquals(0, result.excludedCount)
        assertEquals((1L..160L).toSet(), fixture.saved.map { it.id }.toSet())
        assertTrue(fixture.saved.all { it.updatedAt == Instant.EPOCH })
    }

    @Test
    fun `설정 배수 변경은 실제 산출의 모든 정원에 전달된다`() {
        val fixture = Fixture(mixedApplicants())
        val result = fixture.service(1.5).evaluateFirst(EvaluateScreeningCommand())
        assertEquals(((1L..32L) + (81L..104L) + (121L..160L)).toSet(), fixture.passedIds())
        assertEquals(96, result.passCount)
        assertEquals(64, result.failCount)
    }

    @Test
    fun `실제 산출은 128명까지 전원 합격하고 129명부터 선발한다`() {
        val within = Fixture((1L..128L).map { applicant(it) })
        val over = Fixture((1L..129L).map { applicant(it) })
        assertEquals(128, within.service().evaluateFirst(EvaluateScreeningCommand()).passCount)
        assertEquals((1L..128L).toSet(), within.passedIds())
        assertEquals(104, over.service().evaluateFirst(EvaluateScreeningCommand()).passCount)
        assertEquals((1L..104L).toSet(), over.passedIds())
        assertEquals((105L..129L).toSet(), over.saved.filter { it.status == ApplicantStatus.FIRST_FAIL }.map { it.id }.toSet())
    }

    @Test
    fun `미달 정원은 실제 저장 경로에서도 후순위로 이월하지 않는다`() {
        val fixture = Fixture((1L..150L).map { applicant(it) } +
            (151L..156L).map { applicant(it, AdmissionType.MEISTER, score = 0.0) } +
            (157L..158L).map { applicant(it, AdmissionType.SOCIAL, score = 0.0) })
        assertEquals(112, fixture.service().evaluateFirst(EvaluateScreeningCommand()).passCount)
        assertEquals(((1L..104L) + (151L..158L)).toSet(), fixture.passedIds())
    }

    @Test
    fun `미리보기는 같은 정책으로 계산하고 저장하지 않는다`() {
        val fixture = Fixture(mixedApplicants())
        val result = fixture.service().evaluateFirst(EvaluateScreeningCommand(dryRun = true))
        assertTrue(result.dryRun)
        assertEquals(128, result.passCount)
        assertEquals(32, result.failCount)
        assertTrue(fixture.saved.isEmpty())
    }

    @Test
    fun `접수 완료자는 원본 도착과 수험번호 발급 여부에 관계없이 산출한다`() {
        val fixture = Fixture(listOf(
            applicant(1L).copy(isArrived = true, examineeNumber = "100001"),
            applicant(2L).copy(isArrived = false, examineeNumber = "100002"),
            applicant(3L).copy(isArrived = true, examineeNumber = null),
            applicant(4L).copy(isArrived = false, examineeNumber = null),
        ))
        val result = fixture.service().evaluateFirst(EvaluateScreeningCommand())
        assertEquals(setOf(1L, 2L, 3L, 4L), fixture.passedIds())
        assertEquals(4, result.passCount)
        assertEquals(0, result.excludedCount)
    }

    private fun mixedApplicants() = (1L..80L).map { applicant(it, score = 300.0 - it) } +
        (81L..120L).map { applicant(it, region = Region.DAEJEON, score = 0.0) } +
        (121L..150L).map { applicant(it, AdmissionType.MEISTER, score = 500.0) } +
        (151L..160L).map { applicant(it, AdmissionType.SOCIAL, score = 1000.0) }

    private fun applicant(id: Long, type: AdmissionType = AdmissionType.GENERAL,
        region: Region = Region.NATIONWIDE, score: Double = 1000.0 - id) =
        Applicant(id = id, admissionType = type, region = region, totalScore = score,
            submittedAt = Instant.EPOCH, isArrived = false, examineeNumber = null)

    private class Fixture(val applicants: List<Applicant>) {
        val saved = mutableListOf<Applicant>()
        private val repository = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(ApplicantRepository::class.java)) { _, method, args ->
            when (method.name) {
                "findAll" -> applicants
                "saveAll" -> (args[0] as List<Applicant>).also(saved::addAll)
                else -> error("unexpected call: ${method.name}")
            }
        } as ApplicantRepository
        fun service(multiplier: Double = 2.0) = ScreeningService(repository,
            Clock.fixed(Instant.EPOCH, ZoneOffset.UTC), multiplier)
        fun passedIds() = saved.filter { it.status == ApplicantStatus.FIRST_PASS }.map { it.id }.toSet()
    }
}
