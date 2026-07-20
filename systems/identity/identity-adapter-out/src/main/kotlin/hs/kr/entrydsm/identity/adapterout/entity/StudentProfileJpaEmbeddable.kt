package hs.kr.entrydsm.identity.adapterout.entity

import hs.kr.entrydsm.identity.domain.enum.ApplicantStatus
import hs.kr.entrydsm.identity.domain.enum.PassStatus
import hs.kr.entrydsm.identity.domain.enum.SignupType
import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import java.time.Instant
import java.time.LocalDate

@Embeddable
class StudentProfileJpaEmbeddable(
    @Column(name = "student_name", nullable = false, length = 100)
    val name: String = "",

    @Column(name = "phone", nullable = false, length = 20)
    val phone: String = "",

    @Column(name = "birthdate", nullable = false)
    val birthdate: LocalDate = LocalDate.of(1970, 1, 1),

    @Enumerated(EnumType.STRING)
    @Column(name = "signup_type", nullable = false, length = 20)
    val signupType: SignupType = SignupType.SELF,

    @Enumerated(EnumType.STRING)
    @Column(name = "applicant_status", nullable = false, length = 20)
    var applicantStatus: ApplicantStatus = ApplicantStatus.NONE,

    @Enumerated(EnumType.STRING)
    @Column(name = "pass_status", nullable = false, length = 20)
    var passStatus: PassStatus = PassStatus.NOT_ANNOUNCED,

    @Column(name = "submitted_at")
    var submittedAt: Instant? = null,

    @Column(name = "announced_at")
    var announcedAt: Instant? = null,

    @Column(name = "profile_updated_at", nullable = false)
    var updatedAt: Instant = Instant.EPOCH,
)
