package hs.kr.entrydsm.application.administration.application

import hs.kr.entrydsm.admin.domain.command.UpdateApplicantStatusCommand
import hs.kr.entrydsm.admin.domain.enum.ApplicantStatus
import hs.kr.entrydsm.admin.domain.exception.AdminDomainException
import hs.kr.entrydsm.admin.domain.model.Applicant
import hs.kr.entrydsm.admin.domain.port.`in`.EvaluateFirstScreeningUseCase
import hs.kr.entrydsm.admin.domain.port.out.*
import hs.kr.entrydsm.application.administration.domain.schedule.Schedule
import hs.kr.entrydsm.application.administration.domain.schedule.port.out.ScheduleRepository
import hs.kr.entrydsm.application.application.port.out.FirstScreeningPublicationPolicy
import java.lang.reflect.Proxy
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import org.junit.Assert.*
import org.junit.Test

class ApplicantServiceTest {
    @Test fun `강제 1차 지정과 취소는 지원자 조회보다 먼저 일정 잠금을 획득한다`() {
        for (target in listOf(ApplicantStatus.FIRST_PASS, ApplicantStatus.FIRST_FAIL)) {
            val fixture = Fixture()
            fixture.service().updateStatus(UpdateApplicantStatusCommand(1, target, force = true, reason = "예비 명단 정정"))
            assertEquals(listOf("lock", "findById", if (target == ApplicantStatus.FIRST_PASS) "selectFirst" else "cancelFirst"), fixture.calls)
        }
    }

    @Test fun `강제 사유가 없으면 잠금 후에도 상태를 변경하지 않는다`() {
        val fixture = Fixture()
        assertThrows(AdminDomainException::class.java) {
            fixture.service().updateStatus(UpdateApplicantStatusCommand(1, ApplicantStatus.FIRST_PASS, force = true, reason = " "))
        }
        assertEquals(listOf("lock", "findById"), fixture.calls)
    }

    @Test fun `강제가 아닌 잘못된 상태 전환은 잠금 이후에도 거부한다`() {
        val fixture = Fixture(ApplicantStatus.FIRST_FAIL)
        assertThrows(AdminDomainException::class.java) {
            fixture.service().updateStatus(UpdateApplicantStatusCommand(1, ApplicantStatus.FIRST_PASS))
        }
        assertEquals(listOf("lock", "findById"), fixture.calls)
    }

    @Test fun `1차 일정이 없으면 지원자 조회 없이 거부한다`() {
        val fixture = Fixture()
        assertThrows(AdminDomainException::class.java) {
            fixture.service(withSchedule = false).updateStatus(UpdateApplicantStatusCommand(1, ApplicantStatus.FIRST_PASS, force = true, reason = "정정"))
        }
        assertTrue(fixture.calls.isEmpty())
    }

    @Test fun `다른 상태 변경은 일정 잠금을 추가하지 않는다`() {
        val fixture = Fixture(ApplicantStatus.FIRST_PASS)
        fixture.service(withSchedule = false).updateStatus(UpdateApplicantStatusCommand(1, ApplicantStatus.FINAL_PASS))
        assertEquals(listOf("findById", "save"), fixture.calls)
    }

    @Test fun `일정 저장소에 발표 일정이 없으면 지원자 조회 없이 거부한다`() {
        val fixture = Fixture(hasSchedule = false)
        assertThrows(AdminDomainException::class.java) {
            fixture.service().updateStatus(UpdateApplicantStatusCommand(1, ApplicantStatus.FIRST_PASS, force = true, reason = "정정"))
        }
        assertEquals(listOf("lock"), fixture.calls)
    }

    private class Fixture(status: ApplicantStatus = ApplicantStatus.PENDING, hasSchedule: Boolean = true) {
        val calls = mutableListOf<String>()
        private val applicant = Applicant(1, status = status)
        private val repository = proxy(ApplicantRepository::class.java) { name ->
            calls += name
            when (name) { "findById", "save" -> applicant; else -> error(name) }
        }
        private val screening = proxy(EvaluateFirstScreeningUseCase::class.java) { name -> calls += name; Unit }
        private val schedules = proxy(ScheduleRepository::class.java) { name ->
            assertEquals("findByTitleForUpdate", name)
            calls += "lock"
            if (hasSchedule) Schedule(title = "1차 합격 발표", startAt = LocalDateTime.of(2030, 1, 1, 0, 0), endAt = LocalDateTime.of(2030, 1, 1, 0, 0)) else null
        }
        fun service(withSchedule: Boolean = true) = ApplicantService(repository,
            ApplicantArrivalPort { _, _ -> error("unexpected arrival") }, distancePort = DistancePort { error("unexpected distance") },
            clock = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC), screening = screening,
            publication = FirstScreeningPublicationPolicy { true }, schedules = if (withSchedule) schedules else null)
        private fun <T> proxy(type: Class<T>, call: (String) -> Any?): T = type.cast(
            Proxy.newProxyInstance(javaClass.classLoader, arrayOf(type)) { _, method, _ -> call(method.name) })
    }
}
