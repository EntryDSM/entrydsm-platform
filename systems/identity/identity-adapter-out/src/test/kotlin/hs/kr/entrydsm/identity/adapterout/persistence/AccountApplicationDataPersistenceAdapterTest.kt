package hs.kr.entrydsm.identity.adapterout.persistence

import hs.kr.entrydsm.identity.adapterout.grpc.GrpcApplicationDataAdapter
import hs.kr.entrydsm.identity.adapterout.repository.ApplicationProjectionJpaRepository
import hs.kr.entrydsm.identity.adapterout.repository.IdentityOutboxJpaRepository
import hs.kr.entrydsm.identity.adapterout.repository.StudentProfileJpaRepository
import hs.kr.entrydsm.identity.application.port.out.data.ApplicationStateChangedEvent
import hs.kr.entrydsm.identity.domain.enum.ApplicantStatus
import hs.kr.entrydsm.identity.domain.enum.PassStatus
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verifyNoInteractions

class AccountApplicationDataPersistenceAdapterTest {
    @Test
    fun eventForAccountWithoutStudentProfileIsSkipped() {
        val projectionRepository = mock(ApplicationProjectionJpaRepository::class.java)
        val adapter = AccountApplicationDataPersistenceAdapter(
            projectionRepository,
            mock(IdentityOutboxJpaRepository::class.java),
            mock(GrpcApplicationDataAdapter::class.java),
            mock(StudentProfileJpaRepository::class.java),
        )

        assertEquals(
            false,
            adapter.consume(
                ApplicationStateChangedEvent(
                    eventId = "event-1",
                    userId = 999,
                    applicantId = 10,
                    deleted = false,
                    version = 1,
                    applicantStatus = ApplicantStatus.SUBMITTED,
                    submittedAt = OCCURRED_AT,
                    passStatus = PassStatus.NOT_ANNOUNCED,
                    announcedAt = null,
                    occurredAt = OCCURRED_AT,
                ),
            ),
        )
        verifyNoInteractions(projectionRepository)
    }

    private companion object {
        val OCCURRED_AT: Instant = Instant.parse("2026-06-11T10:00:00Z")
    }
}
