package hs.kr.entrydsm.application.adapterin.api

import hs.kr.entrydsm.application.api.ApplicationApiException
import hs.kr.entrydsm.application.application.exception.ApplicationCancelNotAllowedException
import hs.kr.entrydsm.application.application.port.`in`.ApplicationPort
import hs.kr.entrydsm.application.application.port.`in`.command.CreateApplicantCommand
import hs.kr.entrydsm.application.application.port.`in`.command.SubmitApplicationCommand
import hs.kr.entrydsm.application.application.port.`in`.command.UpdateFamilyCommand
import hs.kr.entrydsm.application.application.port.`in`.command.UpdateIntroductionCommand
import hs.kr.entrydsm.application.application.port.`in`.command.UpdateMiddleSchoolCommand
import hs.kr.entrydsm.application.application.port.`in`.command.UpdatePersonalCommand
import hs.kr.entrydsm.application.application.port.`in`.command.UpdateStudyPlanCommand
import hs.kr.entrydsm.application.application.port.`in`.command.UpdateTypeCommand
import hs.kr.entrydsm.application.application.port.`in`.result.ApplicationSnapshotResult
import hs.kr.entrydsm.application.application.port.`in`.result.CreateApplicantResult
import hs.kr.entrydsm.application.application.port.`in`.result.LandingResult
import hs.kr.entrydsm.application.domain.enum.ApplicantStatus
import hs.kr.entrydsm.application.domain.enum.PassResultStatus
import java.time.Instant
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import hs.kr.entrydsm.application.api.ApplicantStatus as ApiApplicantStatus
import hs.kr.entrydsm.application.api.PassStatus as ApiPassStatus

class ApplicationApiAdapterTest {
    private val port = FakeApplicationPort()
    private val adapter = ApplicationApiAdapter(port)

    @Test
    fun servesCreateGetAndCancelContract() {
        val created = adapter.createApplication(USER_ID)
        val found = adapter.getApplication(USER_ID)
        val canceled = adapter.cancelApplication(USER_ID, "개인 사유")

        assertEquals(ApiApplicantStatus.DRAFT, created.applicantStatus)
        assertEquals(ApiPassStatus.NOT_ANNOUNCED, found.passStatus)
        assertNull(found.submittedAt)
        assertEquals(Instant.parse("2026-09-09T00:00:00Z"), found.updatedAt)
        assertEquals(ApiApplicantStatus.CANCELED, canceled.applicantStatus)
        assertEquals("개인 사유", port.cancelReason)
        assertEquals(2, port.findCount)
    }

    @Test
    fun createReturnsExistingApplicationWithoutCreatingAgain() {
        adapter.createApplication(USER_ID)
        adapter.createApplication(USER_ID)

        assertEquals(1, port.createCount)
    }

    @Test
    fun mapsFinalPassResultsAndAnnouncementTime() {
        port.passStatus = PassResultStatus.FAIL
        port.announcedAt = LocalDateTime.of(2026, 11, 1, 9, 0)
        adapter.createApplication(USER_ID)

        val found = adapter.getApplication(USER_ID)

        assertEquals(ApiPassStatus.FAILED, found.passStatus)
        assertEquals(Instant.parse("2026-11-01T09:00:00Z"), found.announcedAt)
    }

    @Test
    fun mapsInvalidMissingAndNotCancelableApplications() {
        assertThrows(ApplicationApiException.InvalidArgument::class.java) { adapter.getApplication(0) }
        val missing = assertThrows(ApplicationApiException.NotFound::class.java) { adapter.getApplication(404) }
        port.cancelFailure = ApplicationCancelNotAllowedException()
        val notCancelable = assertThrows(ApplicationApiException.CancelNotAllowed::class.java) {
            adapter.cancelApplication(USER_ID, null)
        }

        assertEquals(404L, missing.userId)
        assertEquals(USER_ID, notCancelable.userId)
    }

    @Test
    fun propagatesUnexpectedFailuresUnchanged() {
        port.cancelFailure = IllegalStateException("database unavailable")

        assertThrows(IllegalStateException::class.java) { adapter.cancelApplication(USER_ID, null) }
    }

    private class FakeApplicationPort : ApplicationPort {
        private var snapshot: ApplicationSnapshotResult? = null
        var cancelReason: String? = null
        var cancelFailure: RuntimeException? = null
        var passStatus = PassResultStatus.PENDING
        var announcedAt: LocalDateTime? = null
        var findCount = 0
        var createCount = 0

        override fun createApplicant(command: CreateApplicantCommand): CreateApplicantResult {
            createCount += 1
            snapshot = snapshot(command.userId ?: error("userId is required"), ApplicantStatus.DRAFT)
            return CreateApplicantResult(1L, requireNotNull(snapshot))
        }

        override fun findByUserId(userId: Long): ApplicationSnapshotResult? {
            findCount += 1
            return snapshot?.takeIf { it.userId == userId }
        }

        override fun cancel(userId: Long, reason: String?): ApplicationSnapshotResult {
            cancelFailure?.let { throw it }
            cancelReason = reason
            return snapshot(userId, ApplicantStatus.CANCELED).also { snapshot = it }
        }

        override fun updateType(command: UpdateTypeCommand) = Unit
        override fun updatePersonal(command: UpdatePersonalCommand) = Unit
        override fun updateFamily(command: UpdateFamilyCommand) = Unit
        override fun updateMiddleSchool(command: UpdateMiddleSchoolCommand) = Unit
        override fun updateIntroduction(command: UpdateIntroductionCommand) = Unit
        override fun updateStudyPlan(command: UpdateStudyPlanCommand) = Unit
        override fun submit(command: SubmitApplicationCommand) = Unit
        override fun getLanding(accountId: Long?): LandingResult = LandingResult(null)

        private fun snapshot(userId: Long, status: ApplicantStatus) = ApplicationSnapshotResult(
            userId = userId,
            applicantStatus = status,
            submittedAt = null,
            updatedAt = LocalDateTime.of(2026, 9, 9, 0, 0),
            passStatus = passStatus,
            announcedAt = announcedAt,
        )
    }

    private companion object {
        const val USER_ID = 10L
    }
}
