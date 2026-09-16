package hs.kr.entrydsm.identity.adapterout.persistence

import hs.kr.entrydsm.identity.adapterout.entity.ApplicationProjectionJpaEntity
import hs.kr.entrydsm.identity.adapterout.grpc.GrpcApplicationDataAdapter
import hs.kr.entrydsm.identity.adapterout.repository.ApplicationProjectionJpaRepository
import hs.kr.entrydsm.identity.adapterout.repository.IdentityOutboxJpaRepository
import hs.kr.entrydsm.identity.adapterout.repository.StudentProfileJpaRepository
import hs.kr.entrydsm.identity.application.port.out.data.ApplicationSnapshot
import hs.kr.entrydsm.identity.domain.enum.ApplicantStatus
import hs.kr.entrydsm.identity.domain.enum.PassStatus
import java.time.Instant
import java.util.Optional
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class AccountApplicationDataPersistenceAdapterTest {
    @Test
    fun noneProjectionFallsBackToApplicationService() {
        val projections = mock(ApplicationProjectionJpaRepository::class.java)
        val remote = mock(GrpcApplicationDataAdapter::class.java)
        val draft = ApplicationSnapshot(1, ApplicantStatus.DRAFT, null, Instant.EPOCH, PassStatus.NOT_ANNOUNCED, null)
        `when`(projections.findById(1)).thenReturn(Optional.of(ApplicationProjectionJpaEntity(userId = 1)))
        `when`(remote.findByUserId(1)).thenReturn(draft)

        val result = AccountApplicationDataPersistenceAdapter(
            projections,
            mock(IdentityOutboxJpaRepository::class.java),
            remote,
            mock(StudentProfileJpaRepository::class.java),
        ).findByUserId(1)

        assertEquals(ApplicantStatus.DRAFT, result?.applicantStatus)
    }
}
