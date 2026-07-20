package hs.kr.entrydsm.identity.application.service

import hs.kr.entrydsm.identity.application.port.`in`.AccountPort
import hs.kr.entrydsm.identity.application.port.`in`.ApplicationPort
import hs.kr.entrydsm.identity.application.port.`in`.AuthPort
import hs.kr.entrydsm.identity.application.port.`in`.command.CancelApplicationCommand
import hs.kr.entrydsm.identity.application.port.`in`.command.DeleteAccountCommand
import hs.kr.entrydsm.identity.application.port.`in`.command.LoginCommand
import hs.kr.entrydsm.identity.application.port.`in`.command.LogoutCommand
import hs.kr.entrydsm.identity.application.port.`in`.command.PasswordResetCommand
import hs.kr.entrydsm.identity.application.port.`in`.command.ReadAccountCommand
import hs.kr.entrydsm.identity.application.port.`in`.command.ReadApplicationCommand
import hs.kr.entrydsm.identity.application.port.`in`.command.RefreshTokenCommand
import hs.kr.entrydsm.identity.application.port.`in`.command.SignupCommand
import hs.kr.entrydsm.identity.application.port.`in`.result.AccountResult
import hs.kr.entrydsm.identity.application.port.`in`.result.ApplicationResultResult
import hs.kr.entrydsm.identity.application.port.`in`.result.ApplicationStatusResult
import hs.kr.entrydsm.identity.application.port.`in`.result.BasicInfoResult
import hs.kr.entrydsm.identity.application.port.`in`.result.UserSummaryResult
import hs.kr.entrydsm.identity.application.port.out.AccountRepository
import hs.kr.entrydsm.identity.application.port.out.ApplicationDataPort
import hs.kr.entrydsm.identity.domain.Account
import hs.kr.entrydsm.identity.domain.AccountStatus
import hs.kr.entrydsm.identity.domain.ErrorCode
import hs.kr.entrydsm.identity.domain.PassStatus
import hs.kr.entrydsm.identity.domain.StudentProfile
import hs.kr.entrydsm.identity.domain.exception.IdentityDomainException
import java.util.concurrent.atomic.AtomicLong
import org.springframework.stereotype.Service

@Service
class IdentityService(
    private val accountRepository: AccountRepository,
    private val applicationDataPort: ApplicationDataPort,
) : AuthPort, AccountPort, ApplicationPort {
    private val clock: java.time.Clock = java.time.Clock.systemUTC()
    private val nextUserId = AtomicLong(123L)

    override fun signup(command: SignupCommand): AccountResult {
        requireValidSignup(command)
        if (accountRepository.findByLoginId(command.phone) != null) {
            throw IdentityDomainException(ErrorCode.ACCOUNT_ALREADY_EXISTS)
        }
        val now = now()
        val account = Account.create(
            userId = nextUserId.incrementAndGet(),
            loginId = command.phone,
            password = command.password,
            role = "USER",
            status = AccountStatus.ACTIVE,
            profile = StudentProfile(
                name = command.name,
                phone = command.phone,
                birthdate = command.birthdate,
                signupType = command.signupType,
                updatedAt = now,
            ),
            createdAt = now,
            updatedAt = now,
        )
        accountRepository.save(account)
        applicationDataPort.create(account.userId, now)
        return account.toAccountResult()
    }
    override fun login(command: LoginCommand): UserSummaryResult {
        if (command.loginId.isBlank() || command.password.isBlank()) {
            throw IdentityDomainException(ErrorCode.INVALID_REQUEST_BODY)
        }
        val account = accountRepository.findByLoginId(command.loginId)
            ?: throw IdentityDomainException(ErrorCode.INVALID_CREDENTIALS)
        if (account.status != AccountStatus.ACTIVE) {
            throw IdentityDomainException(ErrorCode.ACCOUNT_INACTIVE)
        }
        if (!account.matchesPassword(command.password)) {
            throw IdentityDomainException(ErrorCode.INVALID_CREDENTIALS)
        }
        return UserSummaryResult(account.userId, account.role, account.status)
    }
    override fun logout(command: LogoutCommand) {
        accountRepository.resolveAccount(command.authorization)
    }
    override fun refreshToken(command: RefreshTokenCommand) {
        val token = command.refreshToken?.takeIf { it.isNotBlank() }
            ?: throw IdentityDomainException(ErrorCode.INVALID_REFRESH_TOKEN)
        if (token.startsWith("expired-")) {
            throw IdentityDomainException(ErrorCode.EXPIRED_REFRESH_TOKEN)
        }
        if (token != "mock-refresh-token") {
            throw IdentityDomainException(ErrorCode.INVALID_REFRESH_TOKEN)
        }
    }
    override fun resetPassword(command: PasswordResetCommand) {
        if (command.loginId.isBlank() || command.name.isBlank() || command.newPassword.isBlank()) {
            throw IdentityDomainException(ErrorCode.INVALID_REQUEST_BODY)
        }
        val account = accountRepository.findByLoginId(command.loginId)
            ?: throw IdentityDomainException(ErrorCode.USER_NOT_FOUND)
        if (account.profile.name != command.name || account.profile.birthdate != command.birthdate) {
            throw IdentityDomainException(ErrorCode.USER_NOT_FOUND)
        }
        account.changePassword(command.newPassword, now())
        accountRepository.save(account)
    }
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

    override fun getApplicationStatus(command: ReadApplicationCommand): ApplicationStatusResult {
        val account = accountRepository.resolveAccount(command.authorization)
        return applicationDataPort.findApplication(account).toStatusResult()
    }

    override fun getApplicationResult(command: ReadApplicationCommand): ApplicationResultResult {
        val account = accountRepository.resolveAccount(command.authorization)
        val application = applicationDataPort.findApplication(account)
        if (application.passStatus == PassStatus.NOT_ANNOUNCED) {
            throw IdentityDomainException(ErrorCode.APPLICATION_RESULT_NOT_AVAILABLE)
        }
        return ApplicationResultResult(application.passStatus, application.announcedAt)
    }

    override fun cancelApplication(command: CancelApplicationCommand): ApplicationStatusResult {
        val account = accountRepository.resolveAccount(command.authorization)
        val application = applicationDataPort.cancel(account.userId, command.reason, now())
        account.profile.applicantStatus = application.applicantStatus
        account.profile.updatedAt = application.updatedAt
        accountRepository.save(account)
        return application.toStatusResult()
    }

    private fun now() = java.time.Instant.now(clock)
}
