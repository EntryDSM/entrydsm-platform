package hs.kr.entrydsm.application.adapterout.entity

import hs.kr.entrydsm.application.domain.enum.ApplicantStatus
import hs.kr.entrydsm.application.domain.enum.PassResultStatus
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

/**
 * 아직 다른 모듈에 전달하지 않은 원서 상태 변경이다. 원서 변경과 같은 트랜잭션에서 저장한다.
 *
 * 이벤트 필드를 컬럼으로 둔다. 같은 프로세스 안에서 전달하므로 직렬화한 바이트를 보관할 이유가 없다.
 */
@Entity
@Table(name = "applicant_status_outbox")
class ApplicantStatusOutboxJpaEntity(
    @Id
    @Column(name = "event_id", length = 36)
    val eventId: String,
    @Column(name = "account_id", nullable = false)
    val accountId: Long,
    @Enumerated(EnumType.STRING)
    @Column(name = "applicant_status", nullable = false, length = 16)
    val applicantStatus: ApplicantStatus,
    @Enumerated(EnumType.STRING)
    @Column(name = "pass_status", nullable = false, length = 16)
    val passStatus: PassResultStatus,
    @Column(name = "status_version", nullable = false)
    val statusVersion: Long,
    @Column(name = "submitted_at")
    val submittedAt: LocalDateTime?,
    @Column(name = "announced_at")
    val announcedAt: LocalDateTime?,
    /** 상태가 바뀐 시각. 전달 순서도 이 값을 따른다. */
    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime,
    @Column(name = "published_at")
    var publishedAt: LocalDateTime? = null,
)
