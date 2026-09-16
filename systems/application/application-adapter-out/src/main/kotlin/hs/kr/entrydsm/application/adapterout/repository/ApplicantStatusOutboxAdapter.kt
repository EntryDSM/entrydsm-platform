package hs.kr.entrydsm.application.adapterout.repository

import hs.kr.entrydsm.application.adapterout.entity.ApplicantStatusOutboxJpaEntity
import hs.kr.entrydsm.application.application.port.out.ApplicantStatusChanged
import hs.kr.entrydsm.application.application.port.out.ApplicantStatusEventOutbox
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

interface ApplicantStatusOutboxJpaRepository : JpaRepository<ApplicantStatusOutboxJpaEntity, String> {
    fun findTop100ByPublishedAtIsNullOrderByCreatedAtAsc(): List<ApplicantStatusOutboxJpaEntity>

    /** 다른 인스턴스가 전달 중인 행은 건너뛴다. 없거나 이미 전달했으면 null 이다. */
    @Query(
        value = "SELECT * FROM applicant_status_outbox " +
            "WHERE event_id = :eventId AND published_at IS NULL FOR UPDATE SKIP LOCKED",
        nativeQuery = true,
    )
    fun findUnpublishedForUpdate(@Param("eventId") eventId: String): ApplicantStatusOutboxJpaEntity?
}

@Repository
class ApplicantStatusOutboxAdapter(
    private val repository: ApplicantStatusOutboxJpaRepository,
) : ApplicantStatusEventOutbox {
    override fun add(event: ApplicantStatusChanged) {
        repository.save(
            ApplicantStatusOutboxJpaEntity(
                eventId = event.eventId.toString(),
                accountId = event.accountId,
                applicantStatus = event.status,
                passStatus = event.passStatus,
                statusVersion = event.version,
                submittedAt = event.submittedAt,
                announcedAt = event.announcedAt,
                createdAt = event.occurredAt,
            ),
        )
    }
}
