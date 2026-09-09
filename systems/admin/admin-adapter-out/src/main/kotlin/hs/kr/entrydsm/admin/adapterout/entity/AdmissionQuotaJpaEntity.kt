package hs.kr.entrydsm.admin.adapterout.entity

import hs.kr.entrydsm.admin.domain.enum.AdmissionType
import hs.kr.entrydsm.admin.domain.enum.Region
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/**
 * 지역 × 전형 조합 하나의 정원입니다. 조합별로 한 행이며 전체가 한 번에 교체된다.
 */
@Entity
@Table(name = "admission_quota")
class AdmissionQuotaJpaEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    val id: Long? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "region", nullable = false, length = 20)
    val region: Region,

    @Enumerated(EnumType.STRING)
    @Column(name = "admission_type", nullable = false, length = 20)
    val admissionType: AdmissionType,

    @Column(name = "quota", nullable = false)
    val quota: Int,

    @Column(name = "updated_at", nullable = false)
    val updatedAt: Instant,

    @Column(name = "updated_by", nullable = false, length = 50)
    val updatedBy: String,
)
