package hs.kr.entrydsm.configuration.adapterout.repository

import hs.kr.entrydsm.configuration.adapterout.entity.EnvironmentVariableJpaEntity
import org.springframework.data.jpa.repository.JpaRepository

interface EnvironmentVariableJpaRepository : JpaRepository<EnvironmentVariableJpaEntity, Long> {
    fun findByKey(key: String): EnvironmentVariableJpaEntity?
    fun deleteByKey(key: String)
    fun existsByKey(key: String): Boolean
}
