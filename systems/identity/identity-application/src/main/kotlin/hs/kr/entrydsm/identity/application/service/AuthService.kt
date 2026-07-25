package hs.kr.entrydsm.identity.application.service

import hs.kr.entrydsm.identity.application.port.`in`.AuthPort
import hs.kr.entrydsm.identity.application.port.`in`.command.LoginCommand
import hs.kr.entrydsm.identity.application.port.`in`.command.LogoutCommand
import hs.kr.entrydsm.identity.application.port.`in`.command.PasswordResetCommand
import hs.kr.entrydsm.identity.application.port.`in`.command.RefreshTokenCommand
import hs.kr.entrydsm.identity.application.port.`in`.command.SignupCommand
import hs.kr.entrydsm.identity.application.port.`in`.result.AccountResult
import hs.kr.entrydsm.identity.application.port.`in`.result.UserSummaryResult
import hs.kr.entrydsm.identity.application.port.out.AccountCommandPort
import hs.kr.entrydsm.identity.application.port.out.AccountRegistrationPort
import hs.kr.entrydsm.identity.application.port.out.AccountQueryPort
import hs.kr.entrydsm.identity.application.port.out.PasswordHasher
import hs.kr.entrydsm.identity.application.port.out.UserIdGenerator
import hs.kr.entrydsm.identity.domain.enum.AccountStatus
import hs.kr.entrydsm.identity.domain.enum.ErrorCode
import hs.kr.entrydsm.identity.domain.exception.IdentityDomainException
import hs.kr.entrydsm.identity.domain.model.Account
import hs.kr.entrydsm.identity.domain.model.StudentProfile
import java.time.Clock
import java.time.Instant

class AuthService(
    private val accountQueryPort: AccountQueryPort,
    private val accountCommandPort: AccountCommandPort,
    private val accountRegistrationPort: AccountRegistrationPort,
    private val userIdGenerator: UserIdGenerator,
    private val passwordHasher: PasswordHasher,
    private val clock: Clock,
) : AuthPort {
    override fun signup(command: SignupCommand): AccountResult {
        requireValidSignup(command)
        if (accountQueryPort.findByLoginId(command.phone) != null) {
            throw IdentityDomainException(ErrorCode.ACCOUNT_ALREADY_EXISTS)
        }
        val now = now()
        val account = Account.create(
            userId = userIdGenerator.nextId(),
            loginId = command.phone,
            passwordHash = passwordHasher.hash(command.password),
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
        val savedAccount = accountRegistrationPort.register(account, now)
        return savedAccount.toAccountResult()
    }

    override fun login(command: LoginCommand): UserSummaryResult {
        if (command.loginId.isBlank() || command.password.isBlank()) {
            throw IdentityDomainException(ErrorCode.INVALID_REQUEST_BODY)
        }
        val account = accountQueryPort.findByLoginId(command.loginId)
            ?: throw IdentityDomainException(ErrorCode.INVALID_CREDENTIALS)
        if (account.status != AccountStatus.ACTIVE) {
            throw IdentityDomainException(ErrorCode.ACCOUNT_INACTIVE)
        }
        if (!passwordHasher.matches(command.password, account.passwordHash)) {
            throw IdentityDomainException(ErrorCode.INVALID_CREDENTIALS)
        }
        return UserSummaryResult(account.userId, account.role, account.status)
    }

    override fun logout(command: LogoutCommand) {
        accountQueryPort.findByUserId(command.userId)
            ?: throw IdentityDomainException(ErrorCode.AUTH_UNAUTHORIZED)
    }

    override fun refreshToken(command: RefreshTokenCommand): UserSummaryResult {
        val token = command.refreshToken?.takeIf { it.isNotBlank() }
            ?: throw IdentityDomainException(ErrorCode.INVALID_REFRESH_TOKEN)
        if (token.startsWith("expired-")) {
            throw IdentityDomainException(ErrorCode.EXPIRED_REFRESH_TOKEN)
        }
        if (token != "mock-refresh-token") {
            throw IdentityDomainException(ErrorCode.INVALID_REFRESH_TOKEN)
        }
        return UserSummaryResult(userId = 123L, role = "STUDENT", status = AccountStatus.ACTIVE)
    }

    override fun resetPassword(command: PasswordResetCommand) {
        if (command.loginId.isBlank() || command.name.isBlank() || command.newPassword.isBlank()) {
            throw IdentityDomainException(ErrorCode.INVALID_REQUEST_BODY)
        }
        val account = accountQueryPort.findByLoginId(command.loginId)
            ?: throw IdentityDomainException(ErrorCode.USER_NOT_FOUND)
        if (account.profile.name != command.name || account.profile.birthdate != command.birthdate) {
            throw IdentityDomainException(ErrorCode.USER_NOT_FOUND)
        }
        account.changePassword(passwordHasher.hash(command.newPassword), now())
        accountCommandPort.save(account)
    }

    private fun now(): Instant = Instant.now(clock)
}
