package hs.kr.entrydsm.application.adapterout.repository

import hs.kr.entrydsm.application.adapterout.entity.ApplicantJpaEntity
import hs.kr.entrydsm.application.application.exception.ScreeningResultChangeNotAllowedException
import hs.kr.entrydsm.application.application.port.`in`.ApplicationPort
import hs.kr.entrydsm.application.application.port.`in`.result.ApplicationFormResult
import hs.kr.entrydsm.application.application.port.out.ApplicantStatusChanged
import hs.kr.entrydsm.application.application.port.out.ApplicantStatusEventOutbox
import hs.kr.entrydsm.application.domain.enum.ApplicantStatus
import hs.kr.entrydsm.application.domain.enum.PassResultStatus
import hs.kr.entrydsm.application.domain.enum.ResultType
import hs.kr.entrydsm.application.domain.enum.SpecialAdmissionType
import hs.kr.entrydsm.application.grpc.PassStatus
import hs.kr.entrydsm.application.grpc.ScreeningResultChangedEvent
import java.lang.reflect.Proxy
import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

class ScreeningResultEventHandlerTest {
    @Test
    fun localChangeContinuesExistingVersionAndPublishesOriginalStatus() {
        val entity = ApplicantJpaEntity(id = 5, accountId = 10, status = ApplicantStatus.ARRIVAL,
            screeningResultVersion = 1200, statusVersion = 3, totalScore = 158.0)
        val events = mutableListOf<ApplicantStatusChanged>()
        val handler = handler(entity, events)
        handler.applyLocal(5, PassStatus.PASS_STATUS_FINAL_PASSED, Instant.EPOCH)
        assertEquals(1201L, entity.screeningResultVersion)
        assertEquals(4L, entity.statusVersion)
        assertEquals(ApplicantStatus.ARRIVAL, entity.status)
        assertEquals(158.0, requireNotNull(entity.totalScore), 0.0)
        assertEquals(ResultType.FINAL, entity.passResults.single().id.resultType)
        assertEquals(PassResultStatus.PASS, entity.passResults.single().result)
        assertEquals(5L, events.single().applicantId)
        assertEquals(10L, events.single().accountId)
        assertEquals(ApplicantStatus.ARRIVAL, events.single().status)
        // 이전 Redis 결과가 늦게 도착해도 새 로컬 결과를 덮어쓰지 않는다.
        handler.consume(ScreeningResultChangedEvent.newBuilder().setApplicantId(5).setVersion(1200)
            .setPassStatus(PassStatus.PASS_STATUS_FIRST_FAILED).build())
        assertEquals(1, events.size)
        assertEquals(ResultType.FINAL, entity.passResults.single().id.resultType)
    }

    @Test
    fun draftAndCanceledApplicationsCannotReceiveLocalResults() {
        for (status in listOf(ApplicantStatus.DRAFT, ApplicantStatus.CANCELED)) {
            val entity = ApplicantJpaEntity(id = 5, accountId = 10, status = status, screeningResultVersion = 1200)
            val events = mutableListOf<ApplicantStatusChanged>()
            assertThrows(ScreeningResultChangeNotAllowedException::class.java) {
                handler(entity, events).applyLocal(5, PassStatus.PASS_STATUS_FIRST_PASSED, Instant.EPOCH)
            }
            assertEquals(1200L, entity.screeningResultVersion)
            assertTrue(entity.passResults.isEmpty())
            assertTrue(events.isEmpty())
        }
    }

    private fun handler(entity: ApplicantJpaEntity, events: MutableList<ApplicantStatusChanged>): ScreeningResultEventHandler {
        val repository = proxy(ApplicantJpaRepository::class.java) { method ->
            assertEquals("findForUpdate", method); entity
        }
        val application = proxy(ApplicationPort::class.java) { method ->
            assertEquals("findApplicationForm", method)
            ApplicationFormResult(applicantId = 5, accountId = 10, status = ApplicantStatus.ARRIVAL,
                name = null, phoneNumber = null, birthdate = null, gender = null, address = null,
                photoFileId = null, region = null, admissionType = null, specialAdmissionType = SpecialAdmissionType.NONE,
                graduationType = null, graduationDate = null, guardianName = null, guardianRelation = null,
                guardianPhoneNumber = null, middleSchool = null, thirdGradeSecondSemester = null,
                thirdGradeFirstSemester = null, previousSemester = null, secondPreviousSemester = null,
                academicRecord = null, score = null, introduction = null, studyPlan = null)
        }
        return ScreeningResultEventHandler(repository, ApplicantStatusEventOutbox { events += it }, application)
    }

    private fun <T> proxy(type: Class<T>, action: (String) -> Any?): T =
        type.cast(Proxy.newProxyInstance(javaClass.classLoader, arrayOf(type)) { _, method, _ -> action(method.name) })
}
