package hs.kr.entrydsm.identity.adapterout.entity

import hs.kr.entrydsm.identity.adapterout.base.BaseTimeEntity
import hs.kr.entrydsm.identity.domain.enum.AccountStatus
import jakarta.persistence.Column
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "accounts")
class AccountJpaEntity(
    @Id
    @Column(name = "user_id")
    val userId: Long = 0,

    @Column(name = "login_id", unique = true, nullable = false, length = 100)
    val loginId: String = "",

    @Column(name = "password", nullable = false, length = 255)
    var password: String = "",

    @Column(name = "role", nullable = false, length = 30)
    val role: String = "USER",

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    var status: AccountStatus = AccountStatus.ACTIVE,

    @Embedded
    val profile: StudentProfileJpaEmbeddable = StudentProfileJpaEmbeddable(),
) : BaseTimeEntity()
