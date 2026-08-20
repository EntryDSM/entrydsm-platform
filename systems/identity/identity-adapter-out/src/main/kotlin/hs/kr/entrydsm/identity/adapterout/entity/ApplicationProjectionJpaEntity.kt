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
import jakarta.persistence.Version
import java.time.Instant

@Entity
@Table(name = "application_projections")
open class ApplicationProjectionJpaEntity(
    @Id
    @Column(name = "user_id")
    val userId: Long = 0,

    @Enumerated(EnumType.STRING)
    @Column(name = "applicant_status", nullable = false, length = 20)
    var applicantStatus: ApplicantStatus = ApplicantStatus.NONE,

    @Column(name = "submitted_at")
    var submittedAt: Instant? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "pass_status", nullable = false, length = 20)
    var passStatus: PassStatus = PassStatus.NOT_ANNOUNCED,

    @Column(name = "announced_at")
    var announcedAt: Instant? = null,

    @Column(name = "state_updated_at", nullable = false)
    var stateUpdatedAt: Instant = Instant.EPOCH,

    @Column(name = "source_version", nullable = false)
    var sourceVersion: Long = 0,

    @Version
    @Column(name = "lock_version", nullable = false)
    var lockVersion: Long = 0,

    @Column(name = "last_event_id", length = 36)
    var lastEventId: String? = null,
) : BaseTimeEntity()
