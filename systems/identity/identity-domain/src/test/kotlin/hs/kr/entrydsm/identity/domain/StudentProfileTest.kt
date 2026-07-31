package hs.kr.entrydsm.identity.domain

import hs.kr.entrydsm.identity.domain.enum.ApplicantStatus
import hs.kr.entrydsm.identity.domain.enum.ErrorCode
import hs.kr.entrydsm.identity.domain.enum.PassStatus
import hs.kr.entrydsm.identity.domain.enum.SignupType
import hs.kr.entrydsm.identity.domain.exception.IdentityDomainException
import hs.kr.entrydsm.identity.domain.model.StudentProfile
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class StudentProfileTest {
    private val initialTime = Instant.parse("2026-06-11T10:00:00Z")
    private val transitionTime = Instant.parse("2026-06-11T11:00:00Z")

    @Test
    fun cancelsSubmittedApplicationAndUpdatesTimestamp() {
        val profile = profile(applicantStatus = ApplicantStatus.SUBMITTED)

        profile.cancel(transitionTime)

        assertEquals(ApplicantStatus.CANCELED, profile.applicantStatus)
        assertEquals(transitionTime, profile.updatedAt)
    }

    @Test
    fun rejectsCancellationForEveryNonSubmittedStatus() {
        listOf(
            ApplicantStatus.NONE,
            ApplicantStatus.DRAFT,
            ApplicantStatus.REVIEWING,
            ApplicantStatus.COMPLETED,
            ApplicantStatus.CANCELED,
        ).forEach { applicantStatus ->
            try {
                profile(applicantStatus).cancel(transitionTime)
                fail("Expected cancellation to be rejected for $applicantStatus")
            } catch (exception: IdentityDomainException) {
                assertEquals(ErrorCode.APPLICATION_CANCEL_NOT_ALLOWED, exception.errorCode)
            }
        }
    }

    @Test
    fun reportsWhetherAnApplicationResultIsAvailable() {
        assertFalse(profile(passStatus = PassStatus.NOT_ANNOUNCED).isResultAvailable())
        assertTrue(profile(passStatus = PassStatus.PASSED).isResultAvailable())
        assertTrue(profile(passStatus = PassStatus.FAILED).isResultAvailable())
    }

    @Test
    fun submitAndAnnounceResultApplyDomainTransitions() {
        val profile = profile()

        profile.submit(transitionTime)
        profile.announceResult(PassStatus.PASSED, transitionTime)

        assertEquals(ApplicantStatus.SUBMITTED, profile.applicantStatus)
        assertEquals(transitionTime, profile.submittedAt)
        assertEquals(PassStatus.PASSED, profile.passStatus)
        assertEquals(transitionTime, profile.announcedAt)
        assertEquals(transitionTime, profile.updatedAt)
    }

    @Test
    fun rejectsSubmitFromTerminalOrInProgressStatuses() {
        listOf(
            ApplicantStatus.SUBMITTED,
            ApplicantStatus.REVIEWING,
            ApplicantStatus.COMPLETED,
            ApplicantStatus.CANCELED,
        ).forEach { applicantStatus ->
            try {
                profile(applicantStatus).submit(transitionTime)
                fail("Expected submission to be rejected for $applicantStatus")
            } catch (exception: IdentityDomainException) {
                assertEquals(ErrorCode.APPLICATION_SUBMIT_NOT_ALLOWED, exception.errorCode)
            }
        }
    }

    @Test
    fun rejectsResultAnnouncementBeforeSubmissionOrAfterAnnouncement() {
        listOf(
            ApplicantStatus.NONE,
            ApplicantStatus.DRAFT,
            ApplicantStatus.CANCELED,
        ).forEach { applicantStatus ->
            try {
                profile(applicantStatus).announceResult(PassStatus.PASSED, transitionTime)
                fail("Expected result announcement to be rejected for $applicantStatus")
            } catch (exception: IdentityDomainException) {
                assertEquals(ErrorCode.APPLICATION_RESULT_ANNOUNCE_NOT_ALLOWED, exception.errorCode)
            }
        }

        val profile = profile(ApplicantStatus.SUBMITTED)
        profile.announceResult(PassStatus.PASSED, transitionTime)
        try {
            profile.announceResult(PassStatus.FAILED, transitionTime)
            fail("Expected an announced result to be immutable")
        } catch (exception: IdentityDomainException) {
            assertEquals(ErrorCode.APPLICATION_RESULT_ANNOUNCE_NOT_ALLOWED, exception.errorCode)
        }
    }

    private fun profile(
        applicantStatus: ApplicantStatus = ApplicantStatus.NONE,
        passStatus: PassStatus = PassStatus.NOT_ANNOUNCED,
    ): StudentProfile = StudentProfile(
        name = "홍길동",
        phone = "01012345678",
        birthdate = LocalDate.parse("2009-03-15"),
        signupType = SignupType.SELF,
        applicantStatus = applicantStatus,
        passStatus = passStatus,
        updatedAt = initialTime,
    )
}
