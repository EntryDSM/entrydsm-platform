package hs.kr.entrydsm.configuration.domain.port.`in`

interface DeleteEnvironmentVariableUseCase {
    fun delete(key: String)
}
