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
    fun `실제 outbox는 원본 합격 결과를 발표 전에는 숨기고 완료 후에만 직렬화한다`() {
        for (published in listOf(false, true)) {
            var saved: hs.kr.entrydsm.application.adapterout.entity.ApplicantStatusOutboxJpaEntity? = null
            val repository = ApplicantStatusOutboxJpaRepository::class.java.cast(Proxy.newProxyInstance(
                javaClass.classLoader, arrayOf(ApplicantStatusOutboxJpaRepository::class.java)) { _, method, args ->
                assertEquals("save", method.name)
                (args[0] as hs.kr.entrydsm.application.adapterout.entity.ApplicantStatusOutboxJpaEntity).also { saved = it }
            })
            val cipher = hs.kr.entrydsm.common.crypto.SnapshotCipher("test",
                mapOf("test" to java.util.Base64.getEncoder().encodeToString(ByteArray(32))))
            val adapter = ApplicantStatusOutboxAdapter(repository, cipher,
                hs.kr.entrydsm.application.application.port.out.FirstScreeningPublicationPolicy { published })
            val announced = java.time.LocalDateTime.of(2026, 10, 30, 1, 0)
            adapter.add(ApplicantStatusChanged(accountId = 10, applicantId = 5, status = ApplicantStatus.SUBMITTED,
                occurredAt = announced, version = 1, submittedAt = announced.minusDays(1), passStatus = PassResultStatus.PASS,
                passResultType = ResultType.DOCUMENT, announcedAt = announced))
            val payload = hs.kr.entrydsm.application.grpc.ApplicantStatusChangedEvent.parseFrom(requireNotNull(saved).payload)
            assertEquals(if (published) PassStatus.PASS_STATUS_FIRST_PASSED else PassStatus.PASS_STATUS_NOT_ANNOUNCED,
                payload.passStatus)
            assertEquals(published, payload.hasAnnouncedAtEpochMillis())
        }
    }

    @Test
    fun `예비 합격 저장은 내부 결과만 변경하고 지원자 이벤트를 발행하지 않는다`() {
        val entity = ApplicantJpaEntity(id = 5, accountId = 10, status = ApplicantStatus.SUBMITTED)
        val events = mutableListOf<ApplicantStatusChanged>()
        val handler = handler(entity, events)
        handler.applyLocal(5, PassStatus.PASS_STATUS_FIRST_PASSED, Instant.EPOCH)
        assertEquals(PassResultStatus.PASS, entity.passResults.single().result)
        assertTrue(events.isEmpty())
        handler.publishFirst(5, Instant.parse("2026-10-30T01:00:00Z"))
        assertEquals(PassResultStatus.PASS, events.single().passStatus)
        assertEquals(java.time.LocalDateTime.of(2026, 10, 30, 1, 0), events.single().announcedAt)
    }

    @Test
    fun `발표 미완료이면 저장된 합격 예정자 공개를 거부한다`() {
        val entity = ApplicantJpaEntity(id = 5, accountId = 10, status = ApplicantStatus.SUBMITTED)
        val events = mutableListOf<ApplicantStatusChanged>()
        val handler = handler(entity, events, published = false)
        handler.applyLocal(5, PassStatus.PASS_STATUS_FIRST_PASSED, Instant.EPOCH)
        assertThrows(IllegalStateException::class.java) { handler.publishFirst(5, Instant.EPOCH) }
        assertTrue(events.isEmpty())
    }

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
    fun `2차 결과는 최종 발표 전에는 1차 결과로 보이고 발표 처리 때 한 번에 알린다`() {
        val entity = ApplicantJpaEntity(id = 5, accountId = 10, status = ApplicantStatus.SUBMITTED)
        val events = mutableListOf<ApplicantStatusChanged>()
        var finalPublished = false
        val handler = handler(entity, events) { finalPublished }
        handler.applyLocal(5, PassStatus.PASS_STATUS_FIRST_PASSED, Instant.EPOCH)
        handler.publishFirst(5, Instant.EPOCH)
        events.clear()

        handler.applyLocal(5, PassStatus.PASS_STATUS_FINAL_PASSED, Instant.EPOCH)
        assertTrue(events.isEmpty())
        assertEquals(ResultType.DOCUMENT, entity.toDomain(finalPublished = false).passResultType)
        assertThrows(IllegalStateException::class.java) { handler.publishFinal(5, Instant.EPOCH) }
        assertTrue(events.isEmpty())

        finalPublished = true
        handler.publishFinal(5, Instant.parse("2026-11-04T01:00:00Z"))
        assertEquals(ResultType.FINAL, events.single().passResultType)
        assertEquals(PassResultStatus.PASS, events.single().passStatus)
        assertEquals(java.time.LocalDateTime.of(2026, 11, 4, 1, 0), events.single().announcedAt)
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

    private fun handler(entity: ApplicantJpaEntity, events: MutableList<ApplicantStatusChanged>, published: Boolean = true,
        finalPublished: () -> Boolean = { true }): ScreeningResultEventHandler {
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
        return ScreeningResultEventHandler(repository, ApplicantStatusEventOutbox { events += it }, application,
            hs.kr.entrydsm.application.application.port.out.FirstScreeningPublicationPolicy { published },
            hs.kr.entrydsm.application.application.port.out.FinalScreeningPublicationPolicy(finalPublished))
    }

    private fun <T> proxy(type: Class<T>, action: (String) -> Any?): T =
        type.cast(Proxy.newProxyInstance(javaClass.classLoader, arrayOf(type)) { _, method, _ -> action(method.name) })
}
