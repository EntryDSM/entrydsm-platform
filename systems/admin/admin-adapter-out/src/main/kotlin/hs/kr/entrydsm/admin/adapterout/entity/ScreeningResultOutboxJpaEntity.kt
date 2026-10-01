package hs.kr.entrydsm.admin.adapterout.entity

import hs.kr.entrydsm.admin.domain.enum.ApplicantStatus
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "screening_result_outbox")
class ScreeningResultOutboxJpaEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    @Column(name = "applicant_id", nullable = false)
    val applicantId: Long,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    val status: ApplicantStatus,
    @Column(name = "created_at", nullable = false)
    val createdAt: Instant,
    @Column(name = "published_at")
    var publishedAt: Instant? = null,
)
