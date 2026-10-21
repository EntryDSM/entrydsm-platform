package hs.kr.entrydsm.application.administration.adapterout.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal

@Entity
@Table(name = "postal_code_locations")
class PostalCodeLocationJpaEntity(
    @Id
    @Column(name = "zip_code", length = 5, columnDefinition = "char(5)")
    val zipCode: String = "",
    @Column(name = "latitude", nullable = false, precision = 17, scale = 14)
    val latitude: BigDecimal = BigDecimal.ZERO,
    @Column(name = "longitude", nullable = false, precision = 18, scale = 14)
    val longitude: BigDecimal = BigDecimal.ZERO,
)
