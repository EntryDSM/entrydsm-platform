package hs.kr.entrydsm.identity.application.service

import hs.kr.entrydsm.identity.application.port.`in`.ApplicationPort
import hs.kr.entrydsm.identity.application.port.`in`.command.CancelApplicationCommand
import hs.kr.entrydsm.identity.application.port.`in`.command.ReadApplicationCommand
import hs.kr.entrydsm.identity.application.port.`in`.result.ApplicationResultResult
import hs.kr.entrydsm.identity.application.port.`in`.result.ApplicationStatusResult
import hs.kr.entrydsm.identity.application.port.out.AccountRepository
import hs.kr.entrydsm.identity.application.port.out.ApplicationDataPort
import hs.kr.entrydsm.identity.domain.enum.ErrorCode
import hs.kr.entrydsm.identity.domain.enum.PassStatus
import hs.kr.entrydsm.identity.domain.exception.IdentityDomainException
import java.time.Clock
import java.time.Instant
import org.springframework.stereotype.Service

@Service
class ApplicationService(
    private val accountRepository: AccountRepository,
    private val applicationDataPort: ApplicationDataPort,
    private val clock: Clock = Clock.systemUTC(),
) : ApplicationPort {
    override fun getApplicationStatus(command: ReadApplicationCommand): ApplicationStatusResult {
        val account = resolveAccount(command.userId)
        return applicationDataPort.findApplication(account).toStatusResult()
    }

    override fun getApplicationResult(command: ReadApplicationCommand): ApplicationResultResult {
        val account = resolveAccount(command.userId)
        val application = applicationDataPort.findApplication(account)
        if (application.passStatus == PassStatus.NOT_ANNOUNCED) {
            throw IdentityDomainException(ErrorCode.APPLICATION_RESULT_NOT_AVAILABLE)
        }
        return ApplicationResultResult(application.passStatus, application.announcedAt)
    }

    override fun cancelApplication(command: CancelApplicationCommand): ApplicationStatusResult {
        val account = resolveAccount(command.userId)
        val application = applicationDataPort.cancel(account.userId, command.reason, now())
        account.profile.cancel(application.updatedAt)
        accountRepository.save(account)
        return application.toStatusResult()
    }

    private fun resolveAccount(userId: Long?) =
        userId?.let(accountRepository::findByUserId)
            ?: throw IdentityDomainException(ErrorCode.AUTH_UNAUTHORIZED)

    private fun now(): Instant = Instant.now(clock)
}
