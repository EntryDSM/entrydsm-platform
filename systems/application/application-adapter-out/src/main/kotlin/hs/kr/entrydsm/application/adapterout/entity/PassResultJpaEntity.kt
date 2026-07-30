package hs.kr.entrydsm.application.adapterout.entity

import hs.kr.entrydsm.application.domain.enum.PassResultStatus
import jakarta.persistence.Column
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.MapsId
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(name = "pass_results")
open class PassResultJpaEntity(
    @EmbeddedId
    var id: PassResultId = PassResultId(),

    @MapsId("applicantId")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "applicant_id")
    var applicant: ApplicantJpaEntity? = null,

    @Column(name = "processed_by")
    var processedBy: Long? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "result", nullable = false, length = 16)
    var result: PassResultStatus = PassResultStatus.PENDING,

    @Column(name = "processed_at")
    var processedAt: LocalDateTime? = null,
)
