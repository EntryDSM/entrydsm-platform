package hs.kr.entrydsm.admin.adapterout.entity

import hs.kr.entrydsm.admin.domain.enum.ApplicantStatus
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/**
 * 지원자의 전형 진행 정보입니다. 원서 내용은 application 이 갖습니다.
 *
 * `applicant_id` 는 application 의 applicantId 라 admin 이 직접 넣습니다. 처음 손대는
 * 지원자(도착 표시·상태 변경·수험 번호 발급)의 행이 그때 만들어지고, 행이 없는 지원자는
 * 미도착·수험 번호 없음·`PENDING` 으로 봅니다.
 */
@Entity
@Table(name = "screening")
class ScreeningJpaEntity(
    @Id
    @Column(name = "applicant_id")
    val applicantId: Long = 0,

    @Column(name = "examinee_number", length = 20)
    val examineeNumber: String? = null,

    @Column(name = "is_arrived", nullable = false)
    val isArrived: Boolean = false,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    val status: ApplicantStatus = ApplicantStatus.PENDING,

    @Column(name = "arrived_at")
    val arrivedAt: Instant? = null,

    @Column(name = "updated_at")
    val updatedAt: Instant? = null,
)
