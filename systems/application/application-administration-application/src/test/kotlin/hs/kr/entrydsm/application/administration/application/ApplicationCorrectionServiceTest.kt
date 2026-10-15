package hs.kr.entrydsm.application.administration.application

import hs.kr.entrydsm.admin.domain.command.ApplicationFormChanges
import hs.kr.entrydsm.admin.domain.command.CorrectApplicationCommand
import hs.kr.entrydsm.admin.domain.exception.AdminDomainException
import hs.kr.entrydsm.admin.domain.port.out.ApplicationCorrectionAuditRepository
import hs.kr.entrydsm.admin.domain.port.out.ApplicationCorrectionState
import hs.kr.entrydsm.admin.domain.port.out.ApplicationCorrectionStatePort
import hs.kr.entrydsm.application.application.port.`in`.ApplicationPort
import hs.kr.entrydsm.application.application.port.out.AccountPhoneValidator
import hs.kr.entrydsm.application.application.port.out.ApplicantRepository
import hs.kr.entrydsm.application.application.port.out.ApplicantStatusEventOutbox
import hs.kr.entrydsm.application.application.port.out.MiddleSchoolRepository
import hs.kr.entrydsm.application.domain.enum.ApplicantStatus
import hs.kr.entrydsm.application.domain.enum.ResultType
import hs.kr.entrydsm.application.domain.model.Applicant
import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.Proxy

class ApplicationCorrectionServiceTest {
    @Test fun `제출 접수이고 내부 결과가 없는 원서만 수정 가능 필드를 제공한다`() {
        for (status in listOf(ApplicantStatus.SUBMITTED, ApplicantStatus.ARRIVAL)) {
            val result = service(Applicant(id = 5, accountId = 10, status = status)).editableFields(5)
            assertTrue(result.editable)
            assertTrue(result.fields.containsAll(listOf("name", "admissionType", "academicRecord")))
            assertFalse(result.fields.contains("examineeNumber"))
            assertNull(result.reason)
        }
    }

    @Test fun `지원자 공개가 대기여도 내부 결과가 있으면 GET과 PATCH 모두 수정 불가다`() {
        val cases = listOf(
            Applicant(id = 5, accountId = 10, status = ApplicantStatus.DRAFT) to false,
            Applicant(id = 5, accountId = 10, status = ApplicantStatus.CANCELED) to false,
            Applicant(id = 5, accountId = 10, status = ApplicantStatus.SUBMITTED, passResultType = ResultType.DOCUMENT) to false,
            Applicant(id = 5, accountId = 10, status = ApplicantStatus.ARRIVAL) to true)
        for ((applicant, hasResult) in cases) {
            val service = service(applicant, hasResult)
            val fields = service.editableFields(5)
            assertFalse(fields.editable)
            assertTrue(fields.fields.isEmpty())
            assertNotNull(fields.reason)
            assertThrows(AdminDomainException::class.java) {
                service.correct(CorrectApplicationCommand(5, "수정 검증", ApplicationFormChanges(introduction = "변경")), "10")
            }
        }
    }

    @Test fun `검정고시 정정에서 기본 다섯 과목 누락은 저장 전에 거부한다`() {
        val complete = hs.kr.entrydsm.admin.domain.command.GedScoresCorrection(100, 100, 100, 100, 100)
        val missing = listOf(complete.copy(korean = null), complete.copy(math = null), complete.copy(english = null),
            complete.copy(science = null), complete.copy(society = null))
        missing.forEach { scores ->
            val applicant = Applicant(id = 5, accountId = 10, status = ApplicantStatus.SUBMITTED,
                graduationType = hs.kr.entrydsm.application.domain.enum.GraduationType.GED)
            val command = CorrectApplicationCommand(5, "성적 정정", ApplicationFormChanges(academicRecord =
                hs.kr.entrydsm.admin.domain.command.AcademicRecordCorrection(0, 0, 0, 0, 0, false, false, gedScores = scores)))
            val failure = assertThrows(AdminDomainException::class.java) { service(applicant).correct(command, "admin") }
            assertEquals(hs.kr.entrydsm.admin.domain.enum.ErrorCode.INVALID_APPLICATION_CORRECTION, failure.errorCode)
        }
    }

    private fun service(applicant: Applicant, hasResult: Boolean = false): ApplicationCorrectionService {
        val repository = proxy(ApplicantRepository::class.java) { method ->
            assertEquals("findForUpdate", method); applicant
        }
        return ApplicationCorrectionService(repository, ApplicationCorrectionStatePort { ApplicationCorrectionState(hasResult) },
            ApplicationCorrectionAuditRepository { error("조회 중 감사 기록을 저장하면 안 된다") },
            proxy(ApplicationPort::class.java) { error("조회 중 원서를 조회할 필요가 없다") },
            ApplicantStatusEventOutbox { error("조회 또는 거부된 수정은 이벤트를 발행하면 안 된다") },
            MiddleSchoolRepository { error("조회 중 학교를 조회할 필요가 없다") }, AccountPhoneValidator { _, _ -> true })
    }

    private fun <T> proxy(type: Class<T>, invoke: (String) -> Any?): T = type.cast(
        Proxy.newProxyInstance(javaClass.classLoader, arrayOf(type)) { _, method, _ -> invoke(method.name) })
}
