package hs.kr.entrydsm.application.administration.application

import hs.kr.entrydsm.admin.domain.command.EvaluateScreeningCommand
import hs.kr.entrydsm.admin.domain.enum.AdmissionType
import hs.kr.entrydsm.admin.domain.enum.ApplicantStatus
import hs.kr.entrydsm.admin.domain.enum.Region
import hs.kr.entrydsm.admin.domain.model.Applicant
import hs.kr.entrydsm.admin.domain.port.out.ApplicantRepository
import hs.kr.entrydsm.application.domain.service.DocumentPassCalculator
import hs.kr.entrydsm.application.administration.domain.schedule.Schedule
import hs.kr.entrydsm.application.administration.domain.schedule.port.out.ScheduleRepository
import java.time.LocalDateTime
import java.lang.reflect.Proxy
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.*
import org.junit.Test

class ScreeningServiceTest {
    @Test
    fun `수동 산출은 설정 배수 1점5로 대전과 후순위를 저장한다`() {
        val fixture = Fixture(mixedApplicants())
        val result = fixture.service(DocumentPassCalculator(1.5)).evaluateFirst(EvaluateScreeningCommand())
        assertEquals(((1L..32L) + (81L..104L) + (121L..160L)).toSet(), fixture.passedIds())
        assertEquals(96, result.passCount)
        assertEquals(64, result.failCount)
    }
    @Test
    fun `실제 수동 산출은 대전 우선과 모든 전형의 후순위를 저장한다`() {
        val fixture = Fixture(mixedApplicants())
        val result = fixture.service().evaluateFirst(EvaluateScreeningCommand())
        assertEquals(((1L..56L) + (81L..112L) + (121L..160L)).toSet(), fixture.passedIds())
        assertEquals(128, result.passCount)
        assertEquals(32, result.failCount)
        assertEquals(0, result.excludedCount)
        assertEquals(fixture.passedIds(), fixture.saved.map { it.id }.toSet())
        assertTrue(fixture.saved.all { it.updatedAt == Instant.EPOCH })
    }

    @Test
    fun `보충 산출은 일반 전형만 있을 때 다른 전형의 미달 정원을 이월하지 않는다`() {
        val within = Fixture((1L..128L).map { applicant(it) })
        val over = Fixture((1L..129L).map { applicant(it) })
        assertEquals(104, within.service().evaluateFirst(EvaluateScreeningCommand()).passCount)
        assertEquals((1L..104L).toSet(), within.passedIds())
        assertEquals(104, over.service().publishFirst().passCount)
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

    @Test
    fun `취소 결과는 보충에서 제외하고 기존 저득점 합격자는 유지한다`() {
        val fixture = Fixture((1L..150L).map {
            applicant(it).copy(status = when (it) {
                1L -> ApplicantStatus.FIRST_FAIL
                150L -> ApplicantStatus.FIRST_PASS
                else -> ApplicantStatus.PENDING
            })
        })
        val result = fixture.service().evaluateFirst(EvaluateScreeningCommand())
        assertEquals(104, result.passCount)
        assertTrue(150L in fixture.passedIds())
        assertFalse(1L in fixture.passedIds())
        assertTrue((2L..104L).all { it in fixture.passedIds() })
    }

    @Test
    fun `개별 취소는 다른 합격 예정자를 수정하지 않는다`() {
        val fixture = Fixture(listOf(applicant(1).copy(status = ApplicantStatus.FIRST_PASS),
            applicant(2).copy(status = ApplicantStatus.FIRST_PASS)))
        fixture.service().cancelFirst(1)
        assertEquals(listOf(1L), fixture.saved.map { it.id })
        assertEquals(ApplicantStatus.FIRST_FAIL, fixture.saved.single().status)
    }

    @Test
    fun `관리자는 취소한 지원자를 다시 합격 예정자로 지정한다`() {
        val fixture = Fixture(listOf(applicant(1).copy(status = ApplicantStatus.FIRST_FAIL)))
        fixture.service().selectFirst(1)
        assertEquals(ApplicantStatus.FIRST_PASS, fixture.saved.single().status)
    }

    @Test
    fun `개인 지정과 취소 반복 요청은 중복 저장하지 않는다`() {
        val fixture = Fixture(listOf(applicant(1)))
        val service = fixture.service()
        service.selectFirst(1)
        service.selectFirst(1)
        service.cancelFirst(1)
        service.cancelFirst(1)
        assertEquals(listOf(ApplicantStatus.FIRST_PASS, ApplicantStatus.FIRST_FAIL), fixture.saved.map { it.status })
    }

    @Test
    fun `정원을 채운 기존 합격자는 점수가 없어도 유지하고 추가 선발하지 않는다`() {
        val fixture = Fixture((1L..128L).map { applicant(it).copy(status = ApplicantStatus.FIRST_PASS, totalScore = null) } + applicant(129))
        val result = fixture.service().publishFirst()
        assertEquals(128, result.passCount)
        assertEquals(ApplicantStatus.FIRST_FAIL, fixture.current[129L]?.status)
        assertTrue((1L..128L).all { fixture.current[it]?.status == ApplicantStatus.FIRST_PASS })
    }

    @Test
    fun `128명 정원을 채운 상태에서 추가 지정은 거부한다`() {
        val fixture = Fixture((1L..128L).map { applicant(it).copy(status = ApplicantStatus.FIRST_PASS) } + applicant(129))
        assertThrows(hs.kr.entrydsm.admin.domain.exception.AdminDomainException::class.java) {
            fixture.service().selectFirst(129)
        }
        assertTrue(fixture.saved.isEmpty())
    }

    @Test
    fun `발표 정각부터 개인 지정과 취소를 거부한다`() {
        val fixture = Fixture(listOf(applicant(1).copy(status = ApplicantStatus.FIRST_PASS), applicant(2)))
        val service = fixture.service(now = Instant.parse("2026-10-30T01:00:00Z"))
        assertThrows(hs.kr.entrydsm.admin.domain.exception.AdminDomainException::class.java) { service.selectFirst(2) }
        assertThrows(hs.kr.entrydsm.admin.domain.exception.AdminDomainException::class.java) { service.cancelFirst(1) }
        assertTrue(fixture.saved.isEmpty())
    }

    @Test
    fun `예비 선발 후 개별 취소와 반복 보충은 미선발 후보를 다시 사용한다`() {
        val fixture = Fixture(mixedApplicants())
        val service = fixture.service()
        service.evaluateFirst(EvaluateScreeningCommand())
        service.cancelFirst(151)
        fixture.saved.clear()
        val result = service.evaluateFirst(EvaluateScreeningCommand())
        assertEquals(128, result.passCount)
        assertEquals(setOf(57L), fixture.saved.map { it.id }.toSet())
        assertEquals(ApplicantStatus.FIRST_FAIL, fixture.current[151L]?.status)
        assertEquals(ApplicantStatus.FIRST_PASS, fixture.current[57L]?.status)
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
        val current = applicants.associateBy { it.id }.toMutableMap()
        private val repository = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(ApplicantRepository::class.java)) { _, method, args ->
            when (method.name) {
                "findAll" -> current.values.toList()
                "findById" -> current[args[0]]
                "save" -> (args[0] as Applicant).also { saved.add(it); current[it.id] = it }
                "saveAll" -> (args[0] as List<Applicant>).also { values -> saved.addAll(values); values.forEach { current[it.id] = it } }
                "publishFirstResults" -> Unit
                else -> error("unexpected call: ${method.name}")
            }
        } as ApplicantRepository
        fun service(calculator: DocumentPassCalculator = DocumentPassCalculator(), now: Instant = Instant.EPOCH) = ScreeningService(repository,
            Clock.fixed(now, ZoneOffset.UTC), calculator,
            Proxy.newProxyInstance(javaClass.classLoader, arrayOf(ScheduleRepository::class.java)) { _, method, _ ->
                when (method.name) {
                    "findByTitle", "findByTitleForUpdate" -> Schedule(title = "1차 합격 발표",
                        startAt = LocalDateTime.of(2026, 10, 30, 10, 0), endAt = LocalDateTime.of(2026, 10, 30, 10, 0))
                    else -> error("unexpected schedule call: ${method.name}")
                }
            } as ScheduleRepository)
        fun passedIds() = current.values.filter { it.status == ApplicantStatus.FIRST_PASS }.map { it.id }.toSet()
    }
}
