package hs.kr.entrydsm.application.adapterout.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Lob
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(name = "applicant_status_outbox")
open class ApplicantStatusOutboxJpaEntity(
    @Id
    @Column(name = "event_id", length = 36)
    val eventId: String,
    @Column(name = "account_id", nullable = false)
    val accountId: Long,
    @Lob
    @Column(name = "payload", nullable = false, columnDefinition = "LONGBLOB")
    val payload: ByteArray,
    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime,
    @Column(name = "published_at")
    var publishedAt: LocalDateTime? = null,
)
