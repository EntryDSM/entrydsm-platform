package hs.kr.entrydsm.application.adapterout.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "institution_codes")
open class InstitutionCodeJpaEntity(
    @Id
    @Column(name = "code", length = 20)
    val code: String,

    @Column(name = "full_name", nullable = false, length = 255)
    val fullName: String,

    @Column(name = "name", nullable = false, length = 255)
    val name: String,

    @Column(name = "postal_code", length = 10)
    val postalCode: String?,

    @Column(name = "status", nullable = false, length = 20)
    val status: String,

    @Column(name = "road_address", length = 500)
    val address: String?,

    @Column(name = "phone_number", length = 30)
    val phoneNumber: String?,

    @Column(name = "fax_number", length = 30)
    val faxNumber: String?,
)