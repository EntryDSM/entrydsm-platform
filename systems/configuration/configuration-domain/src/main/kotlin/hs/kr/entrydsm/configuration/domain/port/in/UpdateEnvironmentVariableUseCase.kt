package hs.kr.entrydsm.configuration.domain.port.`in`

import hs.kr.entrydsm.configuration.domain.EnvironmentVariable
import hs.kr.entrydsm.configuration.domain.command.UpdateEnvironmentVariableCommand

interface UpdateEnvironmentVariableUseCase {
    fun update(command: UpdateEnvironmentVariableCommand): EnvironmentVariable
}
