package hs.kr.entrydsm.configuration.application

import hs.kr.entrydsm.configuration.domain.EnvironmentVariable
import hs.kr.entrydsm.configuration.domain.command.CreateEnvironmentVariableCommand
import hs.kr.entrydsm.configuration.domain.command.UpdateEnvironmentVariableCommand
import hs.kr.entrydsm.configuration.domain.exception.EnvironmentVariableAlreadyExistsException
import hs.kr.entrydsm.configuration.domain.exception.EnvironmentVariableNotFoundException
import hs.kr.entrydsm.configuration.domain.port.`in`.CreateEnvironmentVariableUseCase
import hs.kr.entrydsm.configuration.domain.port.`in`.DeleteEnvironmentVariableUseCase
import hs.kr.entrydsm.configuration.domain.port.`in`.ReadEnvironmentVariableUseCase
import hs.kr.entrydsm.configuration.domain.port.`in`.UpdateEnvironmentVariableUseCase
import hs.kr.entrydsm.configuration.domain.port.out.EnvironmentVariableRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class EnvironmentVariableService(
    private val environmentVariableRepository: EnvironmentVariableRepository,
) : CreateEnvironmentVariableUseCase,
    ReadEnvironmentVariableUseCase,
    UpdateEnvironmentVariableUseCase,
    DeleteEnvironmentVariableUseCase {

    @Transactional
    override fun create(command: CreateEnvironmentVariableCommand): EnvironmentVariable {
        if (environmentVariableRepository.existsByKey(command.key)) {
            throw EnvironmentVariableAlreadyExistsException(command.key)
        }
        return environmentVariableRepository.save(
            EnvironmentVariable(
                key = command.key,
                value = command.value,
                description = command.description,
            )
        )
    }

    override fun findByKey(key: String): EnvironmentVariable =
        environmentVariableRepository.findByKey(key)
            ?: throw EnvironmentVariableNotFoundException(key)

    override fun findAll(): List<EnvironmentVariable> =
        environmentVariableRepository.findAll()

    @Transactional
    override fun update(command: UpdateEnvironmentVariableCommand): EnvironmentVariable {
        val existing = environmentVariableRepository.findByKey(command.key)
            ?: throw EnvironmentVariableNotFoundException(command.key)
        return environmentVariableRepository.save(
            existing.copy(
                value = command.value,
                description = command.description,
            )
        )
    }

    @Transactional
    override fun delete(key: String) {
        if (!environmentVariableRepository.existsByKey(key)) {
            throw EnvironmentVariableNotFoundException(key)
        }
        environmentVariableRepository.deleteByKey(key)
    }
}
