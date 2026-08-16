package hs.kr.entrydsm.identity.adapterout.entity

import hs.kr.entrydsm.identity.adapterout.base.BaseTimeEntity
import hs.kr.entrydsm.identity.domain.enum.AccountStatus
import hs.kr.entrydsm.identity.domain.enum.Role
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "accounts")
open class AccountJpaEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    val id: Long? = null,

    @Column(name = "login_id_hash", unique = true, nullable = false, length = 255)
    var loginIdHash: String = "",

    @Column(name = "login_id_encrypted", length = 255)
    var loginIdEncrypted: String? = null,

    @Column(name = "password_hash", nullable = false, length = 255)
    var passwordHash: String = "",

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    val role: Role = Role.USER,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    var status: AccountStatus = AccountStatus.ACTIVE,
) : BaseTimeEntity()
