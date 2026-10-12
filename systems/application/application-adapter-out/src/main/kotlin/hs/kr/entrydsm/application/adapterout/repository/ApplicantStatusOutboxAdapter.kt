package hs.kr.entrydsm.application.adapterout.repository

import hs.kr.entrydsm.application.adapterout.entity.ApplicantStatusOutboxJpaEntity
import hs.kr.entrydsm.application.application.port.out.ApplicantStatusChanged
import hs.kr.entrydsm.application.application.port.out.ApplicantStatusEventOutbox
import hs.kr.entrydsm.application.grpc.ApplicantStatus
import hs.kr.entrydsm.application.grpc.ApplicantStatusChangedEvent
import hs.kr.entrydsm.application.grpcmapping.toGrpcForm
import hs.kr.entrydsm.common.crypto.SnapshotCipher
import hs.kr.entrydsm.application.grpc.PassStatus
import hs.kr.entrydsm.application.domain.enum.PassResultStatus
import hs.kr.entrydsm.application.domain.enum.ResultType
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

    @org.springframework.transaction.annotation.Transactional
    @org.springframework.data.jpa.repository.Modifying
    @Query(value = "DELETE FROM applicant_status_outbox WHERE published_at < :before LIMIT 100", nativeQuery = true)
    fun deletePublishedBefore(@org.springframework.data.repository.query.Param("before") before: java.time.LocalDateTime): Int
}

@Repository
class ApplicantStatusOutboxAdapter(
    private val repository: ApplicantStatusOutboxJpaRepository,
    private val cipher: SnapshotCipher,
    private val publication: hs.kr.entrydsm.application.application.port.out.FirstScreeningPublicationPolicy = hs.kr.entrydsm.application.application.port.out.FirstScreeningPublicationPolicy { false },
) : ApplicantStatusEventOutbox {
    override fun add(event: ApplicantStatusChanged) {
        // 공개 전에는 다른 원서 변경 이벤트에도 예비 결과를 포함하지 않는다.
        val published = publication.isPublished()
        val payload = ApplicantStatusChangedEvent.newBuilder()
            .setEventId(event.eventId.toString())
            .setAccountId(event.accountId)
            .setApplicantId(event.applicantId)
            .setApplicantStatus(ApplicantStatus.valueOf("APPLICANT_STATUS_${event.status.name}"))
            .setOccurredAtEpochMillis(event.occurredAt.toInstant(ZoneOffset.UTC).toEpochMilli())
            .setVersion(event.version)
            .setApplicantDeleted(event.deleted)
            .setPassStatus(
                when (if (published) event.passStatus else PassResultStatus.PENDING) {
                    PassResultStatus.PENDING -> PassStatus.PASS_STATUS_NOT_ANNOUNCED
                    PassResultStatus.PASS -> when (event.passResultType) {
                        ResultType.DOCUMENT -> PassStatus.PASS_STATUS_FIRST_PASSED
                        ResultType.FINAL -> PassStatus.PASS_STATUS_FINAL_PASSED
                        null -> PassStatus.PASS_STATUS_NOT_ANNOUNCED
                    }
                    PassResultStatus.FAIL -> when (event.passResultType) {
                        ResultType.DOCUMENT -> PassStatus.PASS_STATUS_FIRST_FAILED
                        ResultType.FINAL -> PassStatus.PASS_STATUS_FINAL_FAILED
                        null -> PassStatus.PASS_STATUS_NOT_ANNOUNCED
                    }
                },
            )
            .also { builder ->
                event.applicationForm?.let { builder.setEncryptedApplicationForm(com.google.protobuf.ByteString.copyFrom(cipher.encrypt(it.toGrpcForm().toByteArray()))) }
                if (event.deleted) builder.applicantStatus = ApplicantStatus.APPLICANT_STATUS_NONE
                event.submittedAt?.let { builder.submittedAtEpochMillis = it.toInstant(ZoneOffset.UTC).toEpochMilli() }
                event.announcedAt?.takeIf { published }?.let { builder.announcedAtEpochMillis = it.toInstant(ZoneOffset.UTC).toEpochMilli() }
            }
            .build()
            .toByteArray()
        repository.save(ApplicantStatusOutboxJpaEntity(event.eventId.toString(), event.accountId, payload, event.occurredAt))
    }
}
