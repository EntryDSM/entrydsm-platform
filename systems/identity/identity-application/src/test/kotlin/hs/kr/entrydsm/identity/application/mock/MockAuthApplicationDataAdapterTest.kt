package hs.kr.entrydsm.identity.application.mock

import hs.kr.entrydsm.identity.domain.enum.ApplicantStatus
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
    fun cancelCreatesOrUpdatesCanceledApplication() {
        val snapshot = adapter.cancel(USER_ID, "user request", NOW)

        assertEquals(ApplicantStatus.CANCELED, snapshot.applicantStatus)
        assertNotNull(adapter.findByUserId(USER_ID))
    }

    private companion object {
        const val USER_ID = 123L
        val NOW: Instant = Instant.parse("2026-06-11T10:00:00Z")
    }
}
