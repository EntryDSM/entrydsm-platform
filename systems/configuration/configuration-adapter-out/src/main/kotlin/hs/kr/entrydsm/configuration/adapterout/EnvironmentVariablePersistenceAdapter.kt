package hs.kr.entrydsm.configuration.adapterout

import hs.kr.entrydsm.configuration.adapterout.entity.EnvironmentVariableJpaEntity
import hs.kr.entrydsm.configuration.adapterout.repository.EnvironmentVariableJpaRepository
import hs.kr.entrydsm.configuration.domain.EnvironmentVariable
import hs.kr.entrydsm.configuration.domain.port.out.EnvironmentVariableRepository
import org.springframework.stereotype.Component

@Component
class EnvironmentVariablePersistenceAdapter(
    private val environmentVariableJpaRepository: EnvironmentVariableJpaRepository,
) : EnvironmentVariableRepository {

    override fun save(environmentVariable: EnvironmentVariable): EnvironmentVariable =
        environmentVariableJpaRepository.save(
            EnvironmentVariableJpaEntity.from(environmentVariable)
        ).toDomain()

    override fun findByKey(key: String): EnvironmentVariable? =
        environmentVariableJpaRepository.findByKey(key)?.toDomain()

    override fun findAll(): List<EnvironmentVariable> =
        environmentVariableJpaRepository.findAll().map { it.toDomain() }

    override fun deleteByKey(key: String) =
        environmentVariableJpaRepository.deleteByKey(key)

    override fun existsByKey(key: String): Boolean =
        environmentVariableJpaRepository.existsByKey(key)
}
