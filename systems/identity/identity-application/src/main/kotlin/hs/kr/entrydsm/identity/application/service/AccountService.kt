package hs.kr.entrydsm.identity.application.service

import hs.kr.entrydsm.identity.application.port.`in`.AccountPort
import hs.kr.entrydsm.identity.application.port.`in`.command.DeleteAccountCommand
import hs.kr.entrydsm.identity.application.port.`in`.command.ReadAccountCommand
import hs.kr.entrydsm.identity.application.port.`in`.result.BasicInfoResult
import hs.kr.entrydsm.identity.application.port.out.AccountCommandPort
import hs.kr.entrydsm.identity.application.port.out.AccountQueryPort
import hs.kr.entrydsm.identity.application.port.out.ApplicationDataPort
import hs.kr.entrydsm.identity.domain.enum.ErrorCode
import hs.kr.entrydsm.identity.domain.exception.IdentityDomainException
import java.time.Clock
import java.time.Instant

class AccountService(
    private val accountQueryPort: AccountQueryPort,
    private val accountCommandPort: AccountCommandPort,
    private val applicationDataPort: ApplicationDataPort,
    private val clock: Clock = Clock.systemUTC(),
) : AccountPort {
    override fun deleteAccount(command: DeleteAccountCommand) {
        val account = resolveAccount(command.userId)
        account.delete(now())
        accountCommandPort.save(account)
    }

    override fun getBasicInfo(command: ReadAccountCommand): BasicInfoResult {
        val account = resolveAccount(command.userId)
        val application = applicationDataPort.findApplication(account)
        return BasicInfoResult(
            userId = account.userId,
            role = account.role,
            status = account.status,
            name = account.profile.name,
            phone = account.profile.phone,
            birthdate = account.profile.birthdate,
            signupType = account.profile.signupType,
            applicantStatus = application.applicantStatus,
            createdAt = account.createdAt,
            updatedAt = application.updatedAt,
        )
    }

    private fun resolveAccount(userId: Long?) =
        userId?.let(accountQueryPort::findByUserId)
            ?: throw IdentityDomainException(ErrorCode.AUTH_UNAUTHORIZED)

    private fun now(): Instant = Instant.now(clock)
}
