package hs.kr.entrydsm.identity.adapterout.entity

import hs.kr.entrydsm.identity.adapterout.base.BaseTimeEntity
import hs.kr.entrydsm.identity.domain.enum.ApplicantStatus
import hs.kr.entrydsm.identity.domain.enum.PassStatus
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "identity_outbox")
open class IdentityOutboxJpaEntity(
    @Id
    @Column(name = "event_id", length = 36)
    val eventId: String = "",

    @Column(name = "user_id", nullable = false)
    val userId: Long = 0,

    @Column(name = "event_type", nullable = false, length = 100)
    val eventType: String = "APPLICATION_STATE_CHANGED",

    @Column(name = "source_version", nullable = false)
    val sourceVersion: Long = 0,

    @Enumerated(EnumType.STRING)
    @Column(name = "applicant_status", nullable = false, length = 20)
    val applicantStatus: ApplicantStatus = ApplicantStatus.NONE,

    @Column(name = "submitted_at")
    val submittedAt: Instant? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "pass_status", nullable = false, length = 20)
    val passStatus: PassStatus = PassStatus.NOT_ANNOUNCED,

    @Column(name = "announced_at")
    val announcedAt: Instant? = null,

    @Column(name = "occurred_at", nullable = false)
    val occurredAt: Instant = Instant.EPOCH,

    @Column(name = "reason", length = 255)
    val reason: String? = null,

    @Column(name = "published_at")
    var publishedAt: Instant? = null,

    @Column(name = "attempts", nullable = false)
    var attempts: Int = 0,

    @Column(name = "last_error", length = 1000)
    var lastError: String? = null,
) : BaseTimeEntity()
