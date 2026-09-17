package hs.kr.entrydsm.application.adapterout.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "institution_codes")
open class InstitutionCodeJpaEntity(
    @Id
    @Column(name = "code", length = 7)
    val code: String,
    @Column(name = "full_name", nullable = false, length = 50)
    val fullName: String,
    @Column(name = "name", nullable = false, length = 34)
    val name: String,
    @Column(name = "representative_code", nullable = false, length = 7)
    val representativeCode: String,
    @Column(name = "type", nullable = false, length = 15)
    val type: String,
    @Column(name = "status", nullable = false, length = 2)
    val status: String,
    @Column(name = "registrant", length = 3)
    val registrant: String?,
)
