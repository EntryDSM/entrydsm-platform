package hs.kr.entrydsm.identity.application.service

import hs.kr.entrydsm.identity.application.port.`in`.AccountPort
import hs.kr.entrydsm.identity.application.port.`in`.command.DeleteAccountCommand
import hs.kr.entrydsm.identity.application.port.`in`.command.ReadAccountCommand
import hs.kr.entrydsm.identity.application.port.`in`.result.BasicInfoResult
import hs.kr.entrydsm.identity.application.port.out.AccountRepository
import hs.kr.entrydsm.identity.application.port.out.ApplicationDataPort
import java.time.Clock
import java.time.Instant
import org.springframework.stereotype.Service

@Service
class AccountService(
    private val accountRepository: AccountRepository,
    private val applicationDataPort: ApplicationDataPort,
    private val clock: Clock = Clock.systemUTC(),
) : AccountPort {
    override fun deleteAccount(command: DeleteAccountCommand) {
        val account = accountRepository.resolveAccount(command.authorization)
        account.delete(now())
        accountRepository.save(account)
    }

    override fun getBasicInfo(command: ReadAccountCommand): BasicInfoResult {
        val account = accountRepository.resolveAccount(command.authorization)
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

    private fun now(): Instant = Instant.now(clock)
}
