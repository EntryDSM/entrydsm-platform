package hs.kr.entrydsm.identity.application.service

import hs.kr.entrydsm.identity.application.port.`in`.AuthPort
import hs.kr.entrydsm.identity.application.port.`in`.command.LoginCommand
import hs.kr.entrydsm.identity.application.port.`in`.command.LogoutCommand
import hs.kr.entrydsm.identity.application.port.`in`.command.PasswordResetCommand
import hs.kr.entrydsm.identity.application.port.`in`.command.RefreshTokenCommand
import hs.kr.entrydsm.identity.application.port.`in`.command.SignupCommand
import hs.kr.entrydsm.identity.application.port.`in`.result.AccountResult
import hs.kr.entrydsm.identity.application.port.`in`.result.AuthTokenResult
import hs.kr.entrydsm.identity.application.port.out.AccountCommandPort
import hs.kr.entrydsm.identity.application.port.out.AccountRegistration
import hs.kr.entrydsm.identity.application.port.out.AccountRegistrationPort
import hs.kr.entrydsm.identity.application.port.out.AccountQueryPort
import hs.kr.entrydsm.identity.application.port.out.PasswordHasher
import hs.kr.entrydsm.identity.application.port.out.RefreshTokenRotationStore
import hs.kr.entrydsm.identity.application.port.out.RefreshTokenRevocationStore
import hs.kr.entrydsm.identity.application.security.jwt.JwtTokenVerificationException
import hs.kr.entrydsm.identity.application.security.jwt.JwtTokenGenerator
import hs.kr.entrydsm.identity.application.security.jwt.JwtTokenVerifier
import hs.kr.entrydsm.identity.domain.enum.AccountStatus
import hs.kr.entrydsm.identity.domain.enum.ErrorCode
import hs.kr.entrydsm.identity.domain.exception.IdentityDomainException
import hs.kr.entrydsm.identity.domain.model.StudentProfile
import java.time.Clock
import java.time.Instant

class AuthService(
    private val accountQueryPort: AccountQueryPort,
    private val accountCommandPort: AccountCommandPort,
    private val accountRegistrationPort: AccountRegistrationPort,
    private val passwordHasher: PasswordHasher,
    private val jwtTokenGenerator: JwtTokenGenerator,
    private val jwtTokenVerifier: JwtTokenVerifier,
    private val refreshTokenRotationStore: RefreshTokenRotationStore,
    private val refreshTokenRevocationStore: RefreshTokenRevocationStore,
    private val clock: Clock,
) : AuthPort {
    override fun signup(command: SignupCommand): AccountResult {
        requireValidSignup(command)
        if (accountQueryPort.findByLoginId(command.phone) != null) {
            throw IdentityDomainException(ErrorCode.ACCOUNT_ALREADY_EXISTS)
        }
        val now = now()
        val registration = AccountRegistration(
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
        )
        val savedAccount = accountRegistrationPort.register(registration, now)
        return savedAccount.toAccountResult()
    }

    override fun login(command: LoginCommand): AuthTokenResult {
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
        return issueTokens(account.userId, account.role, account.status)
    }

    override fun logout(command: LogoutCommand) {
        accountQueryPort.findByUserId(command.userId)
            ?: throw IdentityDomainException(ErrorCode.AUTH_UNAUTHORIZED)
        revokeRefreshTokens(command.userId)
    }

    override fun refreshToken(command: RefreshTokenCommand): AuthTokenResult {
        val token = command.refreshToken?.takeIf { it.isNotBlank() }
            ?: throw IdentityDomainException(ErrorCode.INVALID_REFRESH_TOKEN)
        val verifiedToken = try {
            jwtTokenVerifier.verifyRefreshToken(token)
        } catch (exception: JwtTokenVerificationException) {
            val errorCode = when (exception.reason) {
                JwtTokenVerificationException.Reason.EXPIRED -> ErrorCode.EXPIRED_REFRESH_TOKEN
                JwtTokenVerificationException.Reason.INVALID -> ErrorCode.INVALID_REFRESH_TOKEN
            }
            throw IdentityDomainException(errorCode)
        }
        val account = accountQueryPort.findByUserId(verifiedToken.userId)
            ?: throw IdentityDomainException(ErrorCode.INVALID_REFRESH_TOKEN)
        if (account.status != AccountStatus.ACTIVE) {
            throw IdentityDomainException(ErrorCode.ACCOUNT_INACTIVE)
        }
        val currentVersion = refreshTokenState { refreshTokenRevocationStore.currentVersion(verifiedToken.userId) }
        if (verifiedToken.tokenVersion != currentVersion) {
            throw IdentityDomainException(ErrorCode.INVALID_REFRESH_TOKEN)
        }
        val consumed = refreshTokenState {
            refreshTokenRotationStore.consume(verifiedToken.tokenId, verifiedToken.expiresAt)
        }
        if (!consumed) {
            throw IdentityDomainException(ErrorCode.INVALID_REFRESH_TOKEN)
        }
        return issueTokens(
            userId = account.userId,
            role = account.role,
            status = account.status,
            tokenVersion = verifiedToken.tokenVersion,
        )
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
        revokeRefreshTokens(account.userId)
    }

    private fun now(): Instant = Instant.now(clock)

    private fun issueTokens(
        userId: Long,
        role: String,
        status: AccountStatus,
        tokenVersion: Long? = null,
    ): AuthTokenResult {
        val currentTokenVersion = tokenVersion
            ?: refreshTokenState { refreshTokenRevocationStore.currentVersion(userId) }
        return AuthTokenResult(
            userId = userId,
            role = role,
            status = status,
            accessToken = jwtTokenGenerator.generateAccessToken(userSubject(userId), currentTokenVersion),
            refreshToken = jwtTokenGenerator.generateRefreshToken(userSubject(userId), currentTokenVersion),
        )
    }

    private fun revokeRefreshTokens(userId: Long) {
        refreshTokenState { refreshTokenRevocationStore.revokeAll(userId) }
    }

    private fun <T> refreshTokenState(action: () -> T): T = action()

    private fun userSubject(userId: Long): String =
        "${JwtTokenGenerator.USER_PRINCIPAL_PREFIX}$userId"
}
