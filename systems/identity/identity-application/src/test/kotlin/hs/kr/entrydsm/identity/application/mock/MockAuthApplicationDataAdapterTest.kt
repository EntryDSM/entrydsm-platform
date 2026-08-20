package hs.kr.entrydsm.identity.application.mock

import hs.kr.entrydsm.identity.domain.enum.ApplicantStatus
import hs.kr.entrydsm.identity.domain.exception.IdentityDomainException
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Test

class MockAuthApplicationDataAdapterTest {
    private val adapter = MockAuthApplicationDataAdapter()

    @Test
    fun createStoresInitialApplicationState() {
        val snapshot = adapter.create(USER_ID, NOW)

        assertEquals(USER_ID, snapshot.userId)
        assertEquals(ApplicantStatus.NONE, snapshot.applicantStatus)
        assertEquals(snapshot, adapter.findByUserId(USER_ID))
    }

    @Test
    fun cancelRejectsMissingApplication() {
        val exception = assertThrows(IdentityDomainException::class.java) {
            adapter.cancel(USER_ID, "user request", NOW)
        }

        assertEquals("USER_NOT_FOUND", exception.errorCode.name)
        assertEquals(null, adapter.findByUserId(USER_ID))
    }

    @Test
    fun cancelRejectsApplicationThatIsNotSubmitted() {
        adapter.create(USER_ID, NOW)

        val exception = assertThrows(IdentityDomainException::class.java) {
            adapter.cancel(USER_ID, "user request", NOW)
        }

        assertEquals("APPLICATION_CANCEL_NOT_ALLOWED", exception.errorCode.name)
    }

    @Test
    fun cancelUpdatesSubmittedApplication() {
        val submitted = adapter.create(USER_ID, NOW).copy(applicantStatus = ApplicantStatus.SUBMITTED)
        val submittedAdapter = MockAuthApplicationDataAdapter(mapOf(USER_ID to submitted))

        val snapshot = submittedAdapter.cancel(USER_ID, "user request", NOW)

        assertEquals(ApplicantStatus.CANCELED, snapshot.applicantStatus)
        assertNotNull(submittedAdapter.findByUserId(USER_ID))
    }

    private companion object {
        const val USER_ID = 123L
        val NOW: Instant = Instant.parse("2026-06-11T10:00:00Z")
    }
}
