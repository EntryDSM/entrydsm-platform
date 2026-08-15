package hs.kr.entrydsm.identity.adapterout.entity

import hs.kr.entrydsm.identity.adapterout.base.BaseTimeEntity
import hs.kr.entrydsm.identity.domain.enum.ApplicantStatus
import hs.kr.entrydsm.identity.domain.enum.PassStatus
import hs.kr.entrydsm.identity.domain.enum.SignupType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.OneToOne
import jakarta.persistence.Table
import java.time.Instant
import java.time.LocalDate

@Entity
@Table(name = "student_profiles")
open class StudentProfileJpaEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    val id: Long? = null,

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false, unique = true)
    val account: AccountJpaEntity = AccountJpaEntity(),

    @Enumerated(EnumType.STRING)
    @Column(name = "signup_type", nullable = false, length = 10)
    val signupType: SignupType = SignupType.SELF,

    @Column(name = "name_encrypted", nullable = false, length = 255)
    val nameEncrypted: String = "",

    @Column(name = "phone_encrypted", nullable = false, length = 255)
    val phoneEncrypted: String = "",

    @Column(name = "birthdate", nullable = false, columnDefinition = "DATE")
    val birthdate: LocalDate = LocalDate.of(1970, 1, 1),

    @Column(name = "submitted_at")
    var submittedAt: Instant? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "applicant_status", nullable = false, length = 20)
    var applicantStatus: ApplicantStatus = ApplicantStatus.NONE,

    @Enumerated(EnumType.STRING)
    @Column(name = "pass_status", nullable = false, length = 20)
    var passStatus: PassStatus = PassStatus.NOT_ANNOUNCED,

    @Column(name = "announced_at")
    var announcedAt: Instant? = null,
) : BaseTimeEntity()
