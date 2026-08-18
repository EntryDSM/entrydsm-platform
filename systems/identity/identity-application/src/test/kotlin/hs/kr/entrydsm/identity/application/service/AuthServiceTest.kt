package hs.kr.entrydsm.identity.application.service

import hs.kr.entrydsm.identity.application.port.`in`.command.LoginCommand
import hs.kr.entrydsm.identity.application.port.`in`.command.LogoutCommand
import hs.kr.entrydsm.identity.application.port.`in`.command.PasswordResetCommand
import hs.kr.entrydsm.identity.application.port.`in`.command.RefreshTokenCommand
import hs.kr.entrydsm.identity.application.port.`in`.command.SignupCommand
import hs.kr.entrydsm.identity.application.port.out.AccountCommandPort
import hs.kr.entrydsm.identity.application.port.out.AccountAlreadyExistsException
import hs.kr.entrydsm.identity.application.port.out.AccountRegistration
import hs.kr.entrydsm.identity.application.port.out.AccountQueryPort
import hs.kr.entrydsm.identity.application.port.out.AccountRegistrationPort
import hs.kr.entrydsm.identity.application.port.out.PasswordHasher
import hs.kr.entrydsm.identity.application.port.out.PasswordResetOwnershipVerifier
import hs.kr.entrydsm.identity.application.port.out.PassProofStoreUnavailableException
import hs.kr.entrydsm.identity.application.port.out.SignupOwnershipVerifier
import hs.kr.entrydsm.identity.application.port.out.RefreshTokenRotationStore
import hs.kr.entrydsm.identity.application.port.out.RefreshTokenRevocationStore
import hs.kr.entrydsm.identity.domain.enum.AccountStatus
import hs.kr.entrydsm.identity.domain.enum.ApplicantStatus
import hs.kr.entrydsm.identity.domain.enum.ErrorCode
import hs.kr.entrydsm.identity.domain.enum.Role
import hs.kr.entrydsm.identity.domain.enum.SignupType
import hs.kr.entrydsm.identity.domain.exception.IdentityDomainException
import hs.kr.entrydsm.identity.domain.model.Account
import hs.kr.entrydsm.identity.domain.model.PasswordHash
import hs.kr.entrydsm.identity.domain.model.StudentProfile
import hs.kr.entrydsm.identity.application.security.jwt.JwtTokenGenerator
import hs.kr.entrydsm.identity.application.security.jwt.JwtTokenVerifier
import hs.kr.entrydsm.identity.application.security.jwt.TokenType
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class AuthServiceTest {
    private val queryPort = mock(AccountQueryPort::class.java)
    private val commandPort = mock(AccountCommandPort::class.java)
    private val passwordHasher = mock(PasswordHasher::class.java)
    private val consumedRefreshTokenIds = mutableSetOf<String>()
    private val refreshTokenVersions = mutableMapOf<Long, Long>()
    private val clock = Clock.fixed(NOW, UTC)
    private val jwtTokenGenerator = JwtTokenGenerator(SECRET, ISSUER, clock)

    @Test
    fun signupCreatesAccountThroughRegistrationPort() {
        val savedAccount = account()
        var registration: AccountRegistration? = null
        `when`(queryPort.findByLoginId("01012345678")).thenReturn(null)
        `when`(passwordHasher.hash("password123!")).thenReturn(PASSWORD_HASH)
        val service = service(
            AccountRegistrationPort { accountRegistration, _ ->
                registration = accountRegistration
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
        assertEquals(Role.USER, result.role)
        assertEquals("01012345678", registration?.loginId)
        assertEquals(PASSWORD_HASH, registration?.passwordHash)
    }

    @Test
    fun signupRequiresPassOwnershipProof() {
        val thrown = try {
            service(signupOwnershipVerifier = SignupOwnershipVerifier { false }).signup(
                SignupCommand(
                    password = "password123!",
                    name = "홍길동",
                    phone = "01012345678",
                    birthdate = BIRTHDATE,
                    signupType = SignupType.SELF,
                )
            )
            null
        } catch (exception: IdentityDomainException) {
            exception
        }

        assertEquals(ErrorCode.PASS_PROOF_NOT_FOUND, thrown?.errorCode)
    }

    @Test
    fun signupMapsProofStoreFailureToServiceUnavailable() {
        val thrown = try {
            service(
                signupOwnershipVerifier = SignupOwnershipVerifier {
                    throw PassProofStoreUnavailableException(IllegalStateException("redis unavailable"))
                },
            ).signup(
                SignupCommand("password123!", "홍길동", "01012345678", BIRTHDATE, SignupType.SELF)
            )
            null
        } catch (exception: IdentityDomainException) {
            exception
        }

        assertEquals(ErrorCode.PASS_PROOF_STORE_UNAVAILABLE, thrown?.errorCode)
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

    @Test
    fun inactiveAccountCannotLogin() {
        `when`(queryPort.findByLoginId("entry")).thenReturn(account(AccountStatus.INACTIVE))
        `when`(passwordHasher.matches("password123!", PASSWORD_HASH)).thenReturn(true)

        val thrown = try {
            service().login(LoginCommand("entry", "password123!"))
            null
        } catch (exception: IdentityDomainException) {
            exception
        }

        assertEquals(ErrorCode.ACCOUNT_INACTIVE, thrown?.errorCode)
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

    @Test
    fun passwordResetRequiresPassOwnershipProof() {
        val account = account()
        `when`(queryPort.findByLoginId("entry")).thenReturn(account)

        val exception = assertThrows(IdentityDomainException::class.java) {
            service(
                passwordResetOwnershipVerifier = PasswordResetOwnershipVerifier { false },
            ).resetPassword(PasswordResetCommand("entry", "홍길동", BIRTHDATE, "new-password"))
        }

        assertEquals(ErrorCode.PASS_PROOF_NOT_FOUND, exception.errorCode)
        assertEquals(PASSWORD_HASH, account.passwordHash)
        assertTrue(refreshTokenVersions.isEmpty())
        org.mockito.Mockito.verifyNoInteractions(commandPort, passwordHasher)
    }

    @Test(expected = IdentityDomainException::class)
    fun passwordResetRejectsBlankInput() {
        service().resetPassword(
            PasswordResetCommand("", "홍길동", BIRTHDATE, "new-password")
        )
    }

    @Test
    fun refreshTokenIssuesNewAccessAndRefreshTokens() {
        `when`(queryPort.findByUserId(123L)).thenReturn(account())
        val refreshToken = jwtTokenGenerator.generateRefreshToken("user_123").value

        val result = service().refreshToken(RefreshTokenCommand(refreshToken))

        assertEquals(TokenType.ACCESS, result.accessToken.type)
        assertEquals(TokenType.REFRESH, result.refreshToken.type)
        assertEquals(123L, result.userId)
    }

    @Test(expected = IdentityDomainException::class)
    fun invalidRefreshTokenIsRejected() {
        service().refreshToken(RefreshTokenCommand("invalid-refresh-token"))
    }

    @Test(expected = IdentityDomainException::class)
    fun refreshTokenCannotBeUsedTwice() {
        `when`(queryPort.findByUserId(123L)).thenReturn(account())
        val refreshToken = jwtTokenGenerator.generateRefreshToken("user_123").value
        val service = service()

        service.refreshToken(RefreshTokenCommand(refreshToken))
        service.refreshToken(RefreshTokenCommand(refreshToken))
    }

    @Test(expected = IdentityDomainException::class)
    fun refreshTokenIssuedBeforeLogoutIsRejected() {
        `when`(queryPort.findByUserId(123L)).thenReturn(account())
        val refreshToken = jwtTokenGenerator.generateRefreshToken("user_123").value
        val service = service()

        service.logout(LogoutCommand(123L))
        service.refreshToken(RefreshTokenCommand(refreshToken))
    }

    @Test(expected = IdentityDomainException::class)
    fun expiredRefreshTokenIsRejected() {
        val expiredToken = JwtTokenGenerator(
            SECRET,
            ISSUER,
            Clock.fixed(NOW.minus(JwtTokenGenerator.REFRESH_TOKEN_TTL).minusSeconds(1), UTC),
        ).generateRefreshToken("user_123").value

        service().refreshToken(RefreshTokenCommand(expiredToken))
    }

    @Test
    fun logoutChecksTheValidatedUserId() {
        `when`(queryPort.findByUserId(123L)).thenReturn(account())

        service().logout(LogoutCommand(123L))

        org.mockito.Mockito.verify(queryPort).findByUserId(123L)
        assertEquals(1L, refreshTokenVersions[123L])
    }

    @Test
    fun passwordResetPropagatesAccountSaveFailure() {
        val account = account()
        `when`(queryPort.findByLoginId("entry")).thenReturn(account)
        `when`(passwordHasher.hash("new-password")).thenReturn(NEW_PASSWORD_HASH)
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

    @Test
    fun passwordResetDoesNotSaveWhenRefreshTokenRevocationFails() {
        val account = account()
        `when`(queryPort.findByLoginId("entry")).thenReturn(account)
        `when`(passwordHasher.hash("new-password")).thenReturn(NEW_PASSWORD_HASH)
        val failure = IllegalStateException("redis unavailable")
        val revocationStore = mock(RefreshTokenRevocationStore::class.java)
        doThrow(failure).`when`(revocationStore).revokeAll(123L)

        try {
            service(revocationStore = revocationStore).resetPassword(
                PasswordResetCommand("entry", "홍길동", BIRTHDATE, "new-password")
            )
        } catch (exception: IllegalStateException) {
            assertSame(failure, exception)
            assertEquals(PASSWORD_HASH, account.passwordHash)
            org.mockito.Mockito.verifyNoInteractions(commandPort)
            return
        }
        throw AssertionError("refresh-token revocation failure must prevent account save")
    }

    @Test
    fun passwordResetMapsProofStoreFailureToServiceUnavailable() {
        `when`(queryPort.findByLoginId("entry")).thenReturn(account())
        val thrown = try {
            service(
                passwordResetOwnershipVerifier = PasswordResetOwnershipVerifier {
                    throw PassProofStoreUnavailableException(IllegalStateException("redis unavailable"))
                },
            ).resetPassword(PasswordResetCommand("entry", "홍길동", BIRTHDATE, "new-password"))
            null
        } catch (exception: IdentityDomainException) {
            exception
        }

        assertEquals(ErrorCode.PASS_PROOF_STORE_UNAVAILABLE, thrown?.errorCode)
    }

    private fun service(
        registration: AccountRegistrationPort = AccountRegistrationPort { _, _ -> account() },
        signupOwnershipVerifier: SignupOwnershipVerifier = SignupOwnershipVerifier { true },
        passwordResetOwnershipVerifier: PasswordResetOwnershipVerifier = PasswordResetOwnershipVerifier { true },
        revocationStore: RefreshTokenRevocationStore = object : RefreshTokenRevocationStore {
            override fun currentVersion(userId: Long): Long = refreshTokenVersions[userId] ?: 0L

            override fun revokeAll(userId: Long) {
                refreshTokenVersions[userId] = currentVersion(userId) + 1
            }
        },
    ): AuthService = AuthService(
        accountQueryPort = queryPort,
        accountCommandPort = commandPort,
        accountRegistrationPort = registration,
        passwordHasher = passwordHasher,
        jwtTokenGenerator = jwtTokenGenerator,
        jwtTokenVerifier = JwtTokenVerifier(SECRET, ISSUER, clock),
        refreshTokenRotationStore = RefreshTokenRotationStore { tokenId, _ ->
            consumedRefreshTokenIds.add(tokenId)
        },
        refreshTokenRevocationStore = revocationStore,
        clock = clock,
        passwordResetOwnershipVerifier = passwordResetOwnershipVerifier,
        signupOwnershipVerifier = signupOwnershipVerifier,
    )

    private fun account(status: AccountStatus = AccountStatus.ACTIVE): Account {
        return Account.create(
            userId = 123L,
            loginId = "entry",
            passwordHash = PASSWORD_HASH,
            role = Role.USER,
            status = status,
            profile = StudentProfile(
                name = "홍길동",
                phone = "01012345678",
                birthdate = BIRTHDATE,
                signupType = SignupType.SELF,
                applicantStatus = ApplicantStatus.NONE,
                updatedAt = NOW,
            ),
            createdAt = NOW,
            updatedAt = NOW,
        )
    }

    @Test
    fun signupMapsDatabaseDuplicateToAccountAlreadyExists() {
        `when`(queryPort.findByLoginId("01012345678")).thenReturn(null)
        `when`(passwordHasher.hash("password123!")).thenReturn(PASSWORD_HASH)
        val service = service(
            registration = AccountRegistrationPort { _, _ ->
                throw AccountAlreadyExistsException(
                    IllegalStateException("duplicate login id"),
                )
            },
        )

        val thrown = try {
            service.signup(
                SignupCommand(
                    password = "password123!",
                    name = "홍길동",
                    phone = "01012345678",
                    birthdate = BIRTHDATE,
                    signupType = SignupType.SELF,
                )
            )
            null
        } catch (exception: IdentityDomainException) {
            exception
        }

        assertEquals(ErrorCode.ACCOUNT_ALREADY_EXISTS, thrown?.errorCode)
    }

    private companion object {
        val NOW: Instant = Instant.parse("2026-06-11T10:00:00Z")
        val UTC = ZoneOffset.UTC
        val BIRTHDATE: LocalDate = LocalDate.of(2009, 3, 15)
        val PASSWORD_HASH: PasswordHash = PasswordHash.fromEncoded("\$2a\$10\$test")
        const val SECRET = "01234567890123456789012345678901"
        const val ISSUER = "entrydsm-identity"
        val NEW_PASSWORD_HASH: PasswordHash = PasswordHash.fromEncoded("\$2a\$10\$new-password-hash")
    }
}
