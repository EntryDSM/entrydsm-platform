package hs.kr.entrydsm.configuration.domain.port.out

import hs.kr.entrydsm.configuration.domain.EnvironmentVariable

interface EnvironmentVariableRepository {
    fun save(environmentVariable: EnvironmentVariable): EnvironmentVariable
    fun findByKey(key: String): EnvironmentVariable?
    fun findAll(): List<EnvironmentVariable>
    fun deleteByKey(key: String)
    fun existsByKey(key: String): Boolean
}
