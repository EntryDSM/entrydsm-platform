package hs.kr.entrydsm.application.adapterout.repository

import hs.kr.entrydsm.application.adapterout.entity.ApplicantStatusOutboxJpaEntity
import hs.kr.entrydsm.application.application.port.out.ApplicantStatusChanged
import hs.kr.entrydsm.application.application.port.out.ApplicantStatusEventOutbox
import hs.kr.entrydsm.application.grpc.ApplicantStatus
import hs.kr.entrydsm.application.grpc.ApplicantStatusChangedEvent
import hs.kr.entrydsm.application.grpc.PassStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.ZoneOffset

interface ApplicantStatusOutboxJpaRepository : JpaRepository<ApplicantStatusOutboxJpaEntity, String> {
    @Query(
        value = """
            SELECT * FROM applicant_status_outbox
            WHERE published_at IS NULL
            ORDER BY created_at
            LIMIT 100
            FOR UPDATE SKIP LOCKED
        """,
        nativeQuery = true,
    )
    fun findUnpublishedForUpdate(): List<ApplicantStatusOutboxJpaEntity>
}

@Repository
class ApplicantStatusOutboxAdapter(
    private val repository: ApplicantStatusOutboxJpaRepository,
) : ApplicantStatusEventOutbox {
    override fun add(event: ApplicantStatusChanged) {
        val payload = ApplicantStatusChangedEvent.newBuilder()
            .setEventId(event.eventId.toString())
            .setAccountId(event.accountId)
            .setApplicantStatus(ApplicantStatus.valueOf("APPLICANT_STATUS_${event.status.name}"))
            .setOccurredAtEpochMillis(event.occurredAt.toInstant(ZoneOffset.UTC).toEpochMilli())
            .setVersion(event.version)
            .setPassStatus(
                if (event.passStatus.name == "PENDING") PassStatus.PASS_STATUS_NOT_ANNOUNCED
                else PassStatus.valueOf("PASS_STATUS_${event.passStatus.name}"),
            )
            .also { builder ->
                event.submittedAt?.let { builder.submittedAtEpochMillis = it.toInstant(ZoneOffset.UTC).toEpochMilli() }
                event.announcedAt?.let { builder.announcedAtEpochMillis = it.toInstant(ZoneOffset.UTC).toEpochMilli() }
            }
            .build()
            .toByteArray()
        repository.save(ApplicantStatusOutboxJpaEntity(event.eventId.toString(), event.accountId, payload, event.occurredAt))
    }
}
