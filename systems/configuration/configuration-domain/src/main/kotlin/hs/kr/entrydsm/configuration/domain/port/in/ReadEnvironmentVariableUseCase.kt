package hs.kr.entrydsm.configuration.domain.port.`in`

import hs.kr.entrydsm.configuration.domain.EnvironmentVariable

interface ReadEnvironmentVariableUseCase {
    fun findByKey(key: String): EnvironmentVariable
    fun findAll(): List<EnvironmentVariable>
}
