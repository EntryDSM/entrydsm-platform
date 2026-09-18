package hs.kr.entrydsm.application.adapterout.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "institution_codes")
open class InstitutionCodeJpaEntity(
    @Id
    @Column(name = "institution_code", length = 20)
    val institutionCode: String,

    @Column(name = "full_name", nullable = false, length = 255)
    val fullName: String,

    @Column(name = "institution_name", nullable = false, length = 255)
    val institutionName: String,

    @Column(name = "postal_code", length = 10)
    val postalCode: String?,

    @Column(name = "status", nullable = false, length = 20)
    val status: String,

    @Column(name = "road_address", length = 500)
    val roadAddress: String?,

    @Column(name = "phone_number", length = 30)
    val phoneNumber: String?,

    @Column(name = "fax_number", length = 30)
    val faxNumber: String?,
)