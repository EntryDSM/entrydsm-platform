package hs.kr.entrydsm.configuration.adapterout.entity

import hs.kr.entrydsm.configuration.domain.EnvironmentVariable
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "environment_variable")
class EnvironmentVariableJpaEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(name = "env_key", unique = true, nullable = false, length = 255)
    val key: String,

    @Column(name = "env_value", nullable = false, columnDefinition = "TEXT")
    val value: String,

    @Column(name = "description", columnDefinition = "TEXT")
    val description: String? = null,
) {
    fun toDomain() = EnvironmentVariable(
        id = id,
        key = key,
        value = value,
        description = description,
    )

    companion object {
        fun from(domain: EnvironmentVariable) = EnvironmentVariableJpaEntity(
            id = domain.id,
            key = domain.key,
            value = domain.value,
            description = domain.description,
        )
    }
}
