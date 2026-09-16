package hs.kr.entrydsm.identity.adapterout.application

import hs.kr.entrydsm.application.api.ApplicantStatus
import hs.kr.entrydsm.application.api.ApplicationApi
import hs.kr.entrydsm.application.api.ApplicationApiException
import hs.kr.entrydsm.application.api.ApplicationStatusView
import hs.kr.entrydsm.application.api.PassStatus
import hs.kr.entrydsm.identity.domain.enum.ApplicantStatus as DomainApplicantStatus
import hs.kr.entrydsm.identity.domain.enum.ErrorCode
import hs.kr.entrydsm.identity.domain.enum.PassStatus as DomainPassStatus
import hs.kr.entrydsm.identity.domain.exception.IdentityDomainException
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class ApplicationApiDataAdapterTest {
    private val api = FakeApplicationApi()
    private val adapter = ApplicationApiDataAdapter(api)

    @Test
    fun callsApplicationModuleContract() {
        val created = adapter.create(USER_ID, Instant.EPOCH)
        val found = requireNotNull(adapter.findByUserId(USER_ID))
        val canceled = adapter.cancel(USER_ID, "개인 사유", Instant.EPOCH)

        assertEquals(DomainApplicantStatus.DRAFT, created.applicantStatus)
        assertEquals(DomainApplicantStatus.SUBMITTED, found.applicantStatus)
        assertEquals(SUBMITTED_AT, found.submittedAt)
        assertEquals(DomainPassStatus.PASSED, found.passStatus)
        assertEquals(ANNOUNCED_AT, found.announcedAt)
        assertEquals(DomainApplicantStatus.CANCELED, canceled.applicantStatus)
        assertEquals("개인 사유", api.cancelReason)
    }

    @Test
    fun mapsApplicationModuleErrors() {
        assertNull(adapter.findByUserId(NOT_FOUND_USER_ID))

        val notCancelable = assertThrows(IdentityDomainException::class.java) {
            adapter.cancel(NOT_CANCELABLE_USER_ID, null, Instant.EPOCH)
        }
        val missing = assertThrows(IdentityDomainException::class.java) {
            adapter.cancel(NOT_FOUND_USER_ID, null, Instant.EPOCH)
        }
        val invalid = assertThrows(IdentityDomainException::class.java) {
            adapter.create(0, Instant.EPOCH)
        }
        val unexpected = assertThrows(IdentityDomainException::class.java) {
            adapter.create(BROKEN_USER_ID, Instant.EPOCH)
        }

        assertEquals(ErrorCode.APPLICATION_CANCEL_NOT_ALLOWED, notCancelable.errorCode)
        assertEquals(ErrorCode.USER_NOT_FOUND, missing.errorCode)
        assertEquals(ErrorCode.INVALID_REQUEST_BODY, invalid.errorCode)
        assertEquals(ErrorCode.INTERNAL_SERVER_ERROR, unexpected.errorCode)
    }

    private class FakeApplicationApi : ApplicationApi {
        var cancelReason: String? = null

        override fun createApplication(userId: Long): ApplicationStatusView = when (userId) {
            0L -> throw ApplicationApiException.InvalidArgument("user_id must be positive")
            BROKEN_USER_ID -> throw IllegalStateException("database unavailable")
            else -> view(userId, ApplicantStatus.DRAFT)
        }

        override fun getApplication(userId: Long): ApplicationStatusView {
            if (userId == NOT_FOUND_USER_ID) throw ApplicationApiException.NotFound(userId)
            return view(userId, ApplicantStatus.SUBMITTED).copy(
                submittedAt = SUBMITTED_AT,
                passStatus = PassStatus.PASSED,
                announcedAt = ANNOUNCED_AT,
            )
        }

        override fun cancelApplication(userId: Long, reason: String?): ApplicationStatusView {
            when (userId) {
                NOT_FOUND_USER_ID -> throw ApplicationApiException.NotFound(userId)
                NOT_CANCELABLE_USER_ID -> throw ApplicationApiException.CancelNotAllowed(userId)
            }
            cancelReason = reason
            return view(userId, ApplicantStatus.CANCELED)
        }

        private fun view(userId: Long, status: ApplicantStatus) = ApplicationStatusView(
            userId = userId,
            applicantStatus = status,
            submittedAt = null,
            updatedAt = UPDATED_AT,
            passStatus = PassStatus.NOT_ANNOUNCED,
            announcedAt = null,
        )
    }

    private companion object {
        const val USER_ID = 10L
        const val NOT_FOUND_USER_ID = 404L
        const val NOT_CANCELABLE_USER_ID = 409L
        const val BROKEN_USER_ID = 500L
        val UPDATED_AT: Instant = Instant.parse("2026-09-09T00:00:00Z")
        val SUBMITTED_AT: Instant = Instant.parse("2026-09-01T00:00:00Z")
        val ANNOUNCED_AT: Instant = Instant.parse("2026-09-08T00:00:00Z")
    }
}
