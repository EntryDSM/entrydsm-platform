package hs.kr.entrydsm.identity.application.mock

import hs.kr.entrydsm.identity.application.port.`in`.AuthPort
import hs.kr.entrydsm.identity.application.port.`in`.command.LoginCommand
import hs.kr.entrydsm.identity.application.port.`in`.command.LogoutCommand
import hs.kr.entrydsm.identity.application.port.`in`.command.PasswordResetCommand
import hs.kr.entrydsm.identity.application.port.`in`.command.RefreshTokenCommand
import hs.kr.entrydsm.identity.application.port.`in`.command.SignupCommand
import hs.kr.entrydsm.identity.application.port.`in`.result.AccountResult
import hs.kr.entrydsm.identity.application.port.`in`.result.AuthTokenResult
import hs.kr.entrydsm.identity.application.port.`in`.result.ProfileResult
import hs.kr.entrydsm.identity.application.security.jwt.JwtTokenGenerator
import hs.kr.entrydsm.identity.domain.enum.AccountStatus
import java.time.Instant

class MockAuthPortAdapter(
    private val jwtTokenGenerator: JwtTokenGenerator,
) : AuthPort {
    private val now = Instant.parse("2026-06-11T10:00:00Z")

    override fun signup(command: SignupCommand): AccountResult =
        AccountResult(
            userId = 123L,
            role = "USER",
            status = AccountStatus.ACTIVE,
            profile = ProfileResult(
                name = command.name,
                phone = command.phone,
                birthdate = command.birthdate,
                signupType = command.signupType,
                applicantStatus = hs.kr.entrydsm.identity.domain.enum.ApplicantStatus.NONE,
            ),
            createdAt = now,
            updatedAt = now,
        )

    override fun login(command: LoginCommand): AuthTokenResult = issueTokens()

    override fun logout(command: LogoutCommand) = Unit

    override fun refreshToken(command: RefreshTokenCommand): AuthTokenResult = issueTokens()

    override fun resetPassword(command: PasswordResetCommand) = Unit

    private fun issueTokens(): AuthTokenResult = AuthTokenResult(
        userId = 123L,
        role = "STUDENT",
        status = AccountStatus.ACTIVE,
        accessToken = jwtTokenGenerator.generateAccessToken("user_123"),
        refreshToken = jwtTokenGenerator.generateRefreshToken("user_123"),
    )
}
