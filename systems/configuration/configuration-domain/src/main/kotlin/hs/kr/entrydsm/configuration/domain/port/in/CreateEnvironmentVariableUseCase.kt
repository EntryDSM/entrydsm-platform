package hs.kr.entrydsm.configuration.domain.port.`in`

import hs.kr.entrydsm.configuration.domain.EnvironmentVariable
import hs.kr.entrydsm.configuration.domain.command.CreateEnvironmentVariableCommand

interface CreateEnvironmentVariableUseCase {
    fun create(command: CreateEnvironmentVariableCommand): EnvironmentVariable
}
