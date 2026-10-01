package hs.kr.entrydsm.identity.adapterout.persistence

import hs.kr.entrydsm.identity.adapterout.grpc.GrpcApplicationDataAdapter
import hs.kr.entrydsm.identity.adapterout.entity.ApplicationProjectionJpaEntity
import hs.kr.entrydsm.identity.adapterout.entity.StudentProfileJpaEntity
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
import org.mockito.Mockito.`when`

class AccountApplicationDataPersistenceAdapterTest {
    @Test
    fun `최종 합격 이벤트는 학생 프로필과 조회 상태에 반영되고 오래된 결과는 무시한다`() {
        val profiles = mock(StudentProfileJpaRepository::class.java)
        val projections = mock(ApplicationProjectionJpaRepository::class.java)
        val profile = StudentProfileJpaEntity()
        val projection = ApplicationProjectionJpaEntity(userId = 1, applicantId = 10)
        `when`(profiles.findByAccount_Id(1)).thenReturn(profile)
        `when`(projections.findByUserIdForUpdate(1)).thenReturn(projection)
        val adapter = AccountApplicationDataPersistenceAdapter(projections, mock(IdentityOutboxJpaRepository::class.java),
            mock(GrpcApplicationDataAdapter::class.java), profiles)
        val final = ApplicationStateChangedEvent(eventId = "final", userId = 1, applicantId = 10, deleted = false,
            version = 2, applicantStatus = ApplicantStatus.SUBMITTED, submittedAt = OCCURRED_AT,
            passStatus = PassStatus.FINAL_PASSED, announcedAt = OCCURRED_AT, occurredAt = OCCURRED_AT)
        assertEquals(true, adapter.consume(final))
        assertEquals(PassStatus.FINAL_PASSED, profile.passStatus)
        assertEquals(PassStatus.FINAL_PASSED, projection.passStatus)
        assertEquals(OCCURRED_AT, profile.announcedAt)
        assertEquals(false, adapter.consume(final.copy(eventId = "first", version = 1, passStatus = PassStatus.FIRST_FAILED)))
        assertEquals(PassStatus.FINAL_PASSED, profile.passStatus)
        assertEquals(true, adapter.consume(final.copy(eventId = "reset", version = 3, passStatus = PassStatus.NOT_ANNOUNCED, announcedAt = null)))
        assertEquals(PassStatus.NOT_ANNOUNCED, profile.passStatus)
        assertEquals(null, profile.announcedAt)
    }

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
