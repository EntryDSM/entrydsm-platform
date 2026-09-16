package hs.kr.entrydsm.application.adapterout.repository

import hs.kr.entrydsm.application.adapterout.entity.ApplicantStatusOutboxJpaEntity
import hs.kr.entrydsm.application.domain.enum.ApplicantStatus
import hs.kr.entrydsm.application.domain.enum.PassResultStatus
import java.time.Instant
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import hs.kr.entrydsm.application.api.ApplicantStatus as ApiApplicantStatus
import hs.kr.entrydsm.application.api.PassStatus as ApiPassStatus

class ApplicantStatusOutboxEventTest {
    @Test
    fun mapsStoredChangeToPublishedEvent() {
        val event = outbox(PassResultStatus.PENDING, announcedAt = null).toApplicantStatusChangedEvent()

        assertEquals(EVENT_ID, event.eventId)
        assertEquals(10L, event.accountId)
        assertEquals(ApiApplicantStatus.SUBMITTED, event.applicantStatus)
        assertEquals(ApiPassStatus.NOT_ANNOUNCED, event.passStatus)
        assertEquals(3L, event.version)
        assertEquals(Instant.parse("2026-09-01T09:00:00Z"), event.submittedAt)
        assertNull(event.announcedAt)
        assertEquals(Instant.parse("2026-09-02T10:00:00Z"), event.occurredAt)
    }

    /** protobuf 로 옮기던 시절 PASS·FAIL 을 `PASS_STATUS_PASS` 로 찾다가 실패하던 경로다. */
    @Test
    fun mapsFinalPassResults() {
        val announcedAt = LocalDateTime.of(2026, 11, 1, 9, 0)

        assertEquals(ApiPassStatus.PASSED, outbox(PassResultStatus.PASS, announcedAt).toApplicantStatusChangedEvent().passStatus)
        assertEquals(ApiPassStatus.FAILED, outbox(PassResultStatus.FAIL, announcedAt).toApplicantStatusChangedEvent().passStatus)
        assertEquals(
            Instant.parse("2026-11-01T09:00:00Z"),
            outbox(PassResultStatus.PASS, announcedAt).toApplicantStatusChangedEvent().announcedAt,
        )
    }

    private fun outbox(passStatus: PassResultStatus, announcedAt: LocalDateTime?) = ApplicantStatusOutboxJpaEntity(
        eventId = EVENT_ID,
        accountId = 10L,
        applicantStatus = ApplicantStatus.SUBMITTED,
        passStatus = passStatus,
        statusVersion = 3L,
        submittedAt = LocalDateTime.of(2026, 9, 1, 9, 0),
        announcedAt = announcedAt,
        createdAt = LocalDateTime.of(2026, 9, 2, 10, 0),
    )

    private companion object {
        const val EVENT_ID = "3f2c1e5a-8a55-4a1b-9c62-5d1f0e7a9b10"
    }
}
