package hs.kr.entrydsm.identity.application.service

import hs.kr.entrydsm.identity.application.port.`in`.command.LoginCommand
import hs.kr.entrydsm.identity.application.port.`in`.command.LogoutCommand
import hs.kr.entrydsm.identity.application.port.`in`.command.PasswordResetCommand
import hs.kr.entrydsm.identity.application.port.`in`.command.SignupCommand
import hs.kr.entrydsm.identity.application.port.out.AccountCommandPort
import hs.kr.entrydsm.identity.application.port.out.AccountQueryPort
import hs.kr.entrydsm.identity.application.port.out.AccountRegistrationPort
import hs.kr.entrydsm.identity.application.port.out.PasswordHasher
import hs.kr.entrydsm.identity.application.port.out.UserIdGenerator
import hs.kr.entrydsm.identity.domain.enum.AccountStatus
import hs.kr.entrydsm.identity.domain.enum.ApplicantStatus
import hs.kr.entrydsm.identity.domain.enum.SignupType
import hs.kr.entrydsm.identity.domain.exception.IdentityDomainException
import hs.kr.entrydsm.identity.domain.model.Account
import hs.kr.entrydsm.identity.domain.model.PasswordHash
import hs.kr.entrydsm.identity.domain.model.StudentProfile
import hs.kr.entrydsm.identity.application.security.jwt.TokenType
import hs.kr.entrydsm.identity.application.security.jwt.JwtTokenGenerator
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class AuthServiceTest {
    private val queryPort = mock(AccountQueryPort::class.java)
    private val commandPort = mock(AccountCommandPort::class.java)
    private val passwordHasher = mock(PasswordHasher::class.java)
    private val userIdGenerator = UserIdGenerator { 124L }
    private val clock = Clock.fixed(NOW, UTC)
    private val jwtTokenGenerator = JwtTokenGenerator(SECRET, ISSUER, clock)

    @Test
    fun signupCreatesAccountThroughRegistrationPort() {
        val savedAccount = account()
        var registeredAccount: Account? = null
        `when`(queryPort.findByLoginId("01012345678")).thenReturn(null)
        `when`(passwordHasher.hash("password123!")).thenReturn(PASSWORD_HASH)
        val service = service(
            AccountRegistrationPort { account, _ ->
                registeredAccount = account
                savedAccount
            }
        )

        val result = service.signup(
            SignupCommand(
                password = "password123!",
                name = "홍길동",
                phone = "01012345678",
                birthdate = BIRTHDATE,
                signupType = SignupType.SELF,
            )
        )

        assertEquals(123L, result.userId)
        assertEquals("USER", result.role)
        assertEquals(124L, registeredAccount?.userId)
    }

    @Test(expected = IdentityDomainException::class)
    fun signupRejectsDuplicateLoginId() {
        `when`(queryPort.findByLoginId("01012345678")).thenReturn(account())

        service().signup(
            SignupCommand(
                password = "password123!",
                name = "홍길동",
                phone = "01012345678",
                birthdate = BIRTHDATE,
                signupType = SignupType.SELF,
            )
        )
    }

    @Test(expected = IdentityDomainException::class)
    fun inactiveAccountCannotLogin() {
        `when`(queryPort.findByLoginId("entry")).thenReturn(account(AccountStatus.INACTIVE))

        service().login(LoginCommand("entry", "password123!"))
    }

    @Test
    fun validLoginIssuesAccessAndRefreshTokens() {
        `when`(queryPort.findByLoginId("entry")).thenReturn(account())
        `when`(passwordHasher.matches("password123!", PASSWORD_HASH)).thenReturn(true)

        val result = service().login(LoginCommand("entry", "password123!"))

        assertEquals(TokenType.ACCESS, result.accessToken.type)
        assertEquals(TokenType.REFRESH, result.refreshToken.type)
        assertEquals(123L, result.userId)
    }

    @Test(expected = IdentityDomainException::class)
    fun invalidPasswordCannotLogin() {
        `when`(queryPort.findByLoginId("entry")).thenReturn(account())
        `when`(passwordHasher.matches("wrong-password", PASSWORD_HASH)).thenReturn(false)

        service().login(LoginCommand("entry", "wrong-password"))
    }

    @Test(expected = IdentityDomainException::class)
    fun blankLoginInputIsRejected() {
        service().login(LoginCommand("", "password123!"))
    }

    @Test(expected = IdentityDomainException::class)
    fun passwordResetRejectsUnknownUser() {
        `when`(queryPort.findByLoginId("entry")).thenReturn(null)

        service().resetPassword(
            PasswordResetCommand("entry", "홍길동", BIRTHDATE, "new-password")
        )
    }

    @Test(expected = IdentityDomainException::class)
    fun passwordResetRejectsMismatchedProfile() {
        `when`(queryPort.findByLoginId("entry")).thenReturn(account())

        service().resetPassword(
            PasswordResetCommand("entry", "다른이름", BIRTHDATE, "new-password")
        )
    }

    @Test(expected = IdentityDomainException::class)
    fun passwordResetRejectsBlankInput() {
        service().resetPassword(
            PasswordResetCommand("", "홍길동", BIRTHDATE, "new-password")
        )
    }

    @Test
    fun refreshTokenIssuesNewAccessAndRefreshTokens() {
        val result = service().refreshToken(RefreshTokenCommand("mock-refresh-token"))

        assertEquals(TokenType.ACCESS, result.accessToken.type)
        assertEquals(TokenType.REFRESH, result.refreshToken.type)
        assertEquals(123L, result.userId)
    }

    @Test(expected = IdentityDomainException::class)
    fun invalidRefreshTokenIsRejected() {
        service().refreshToken(RefreshTokenCommand("invalid-refresh-token"))
    }

    @Test
    fun logoutChecksTheValidatedUserId() {
        `when`(queryPort.findByUserId(123L)).thenReturn(account())

        service().logout(LogoutCommand(123L))

        org.mockito.Mockito.verify(queryPort).findByUserId(123L)
    }

    @Test
    fun passwordResetPropagatesAccountSaveFailure() {
        val account = account()
        `when`(queryPort.findByLoginId("entry")).thenReturn(account)
        `when`(passwordHasher.hash("new-password")).thenReturn(PASSWORD_HASH)
        val failure = IllegalStateException("save failed")
        doThrow(failure).`when`(commandPort).save(account)

        try {
            service().resetPassword(
                PasswordResetCommand("entry", "홍길동", BIRTHDATE, "new-password")
            )
        } catch (exception: IllegalStateException) {
            assertSame(failure, exception)
            return
        }
        throw AssertionError("account save failure must be propagated")
    }

    private fun service(
        registration: AccountRegistrationPort = AccountRegistrationPort { account -> account },
    ): AuthService = AuthService(
        accountQueryPort = queryPort,
        accountCommandPort = commandPort,
        accountRegistrationPort = registration,
        userIdGenerator = userIdGenerator,
        passwordHasher = passwordHasher,
        jwtTokenGenerator = jwtTokenGenerator,
        clock = clock,
    )

    private fun account(status: AccountStatus = AccountStatus.ACTIVE): Account {
        val account = mock(Account::class.java)
        val profile = mock(StudentProfile::class.java)
        `when`(account.userId).thenReturn(123L)
        `when`(account.role).thenReturn("USER")
        `when`(account.status).thenReturn(status)
        `when`(account.profile).thenReturn(profile)
        `when`(account.passwordHash).thenReturn(PASSWORD_HASH)
        `when`(account.createdAt).thenReturn(NOW)
        `when`(account.updatedAt).thenReturn(NOW)
        `when`(profile.name).thenReturn("홍길동")
        `when`(profile.phone).thenReturn("01012345678")
        `when`(profile.birthdate).thenReturn(BIRTHDATE)
        `when`(profile.signupType).thenReturn(SignupType.SELF)
        `when`(profile.applicantStatus).thenReturn(ApplicantStatus.NONE)
        return account
    }

    private companion object {
        val NOW: Instant = Instant.parse("2026-06-11T10:00:00Z")
        val UTC = ZoneOffset.UTC
        val BIRTHDATE: LocalDate = LocalDate.of(2009, 3, 15)
        val PASSWORD_HASH: PasswordHash = PasswordHash.fromEncoded("\$2a\$10\$test")
        const val SECRET = "01234567890123456789012345678901"
        const val ISSUER = "entrydsm-identity"
    }
}
