package hs.kr.entrydsm.identity.adapterin.web

import hs.kr.entrydsm.identity.adapterin.web.dto.request.LoginRequest
import hs.kr.entrydsm.identity.adapterin.web.dto.request.SignupRequest
import hs.kr.entrydsm.identity.application.port.`in`.AuthPort
import hs.kr.entrydsm.identity.application.port.`in`.command.LoginCommand
import hs.kr.entrydsm.identity.application.port.`in`.command.LogoutCommand
import hs.kr.entrydsm.identity.application.port.`in`.command.PasswordResetCommand
import hs.kr.entrydsm.identity.application.port.`in`.command.RefreshTokenCommand
import hs.kr.entrydsm.identity.application.port.`in`.command.SignupCommand
import hs.kr.entrydsm.identity.application.port.`in`.result.AccountResult
import hs.kr.entrydsm.identity.application.port.`in`.result.AuthTokenResult
import hs.kr.entrydsm.identity.application.security.AuthenticatedUser
import hs.kr.entrydsm.identity.application.port.`in`.result.ProfileResult
import hs.kr.entrydsm.identity.domain.enum.AccountStatus
import hs.kr.entrydsm.identity.domain.enum.ApplicantStatus
import hs.kr.entrydsm.identity.domain.enum.Role
import hs.kr.entrydsm.identity.domain.enum.SignupType
import java.time.Instant
import java.time.LocalDate
import java.time.Clock
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import hs.kr.entrydsm.identity.application.security.jwt.JwtTokenGenerator

class AuthControllerTest {
    @Test
    fun signupMapsRequestToCommandAndReturnsCreatedAccount() {
        val authPort = FakeAuthPort()
        val controller = AuthController(authPort)
        val birthdate = LocalDate.parse("2009-03-15")

        val response = controller.signup(
            SignupRequest(
                password = "password123!",
                name = "홍길동",
                phone = "01012345678",
                birthdate = birthdate,
                signupType = SignupType.SELF,
            )
        )

        val command = requireNotNull(authPort.signupCommand)
        assertEquals("password123!", command.password)
        assertEquals("홍길동", command.name)
        assertEquals("01012345678", command.phone)
        assertEquals(birthdate, command.birthdate)
        assertEquals(SignupType.SELF, command.signupType)
        assertEquals(HttpStatus.CREATED, response.statusCode)
        assertEquals("/api/identity/v11/accounts/me", response.headers.location.toString())
        assertEquals("홍길동", response.body?.data?.profile?.name)
    }

    @Test
    fun loginMapsRequestAndSetsHttpOnlyTokenCookies() {
        val authPort = FakeAuthPort()
        val controller = AuthController(authPort)

        val response = controller.login(LoginRequest(loginId = "entry", password = "password123!"))

        val command = requireNotNull(authPort.loginCommand)
        val cookies = response.headers[HttpHeaders.SET_COOKIE].orEmpty()
        assertEquals("entry", command.loginId)
        assertEquals("password123!", command.password)
        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals("user_123", response.body?.data?.userId)
        assertTrue(cookies.any { it.contains("access_token=ey") && it.contains("HttpOnly") })
        assertTrue(cookies.any { it.contains("refresh_token=ey") && it.contains("HttpOnly") })
    }

    @Test
    fun cookieSecureAttributeFollowsEnvironmentSetting() {
        val response = AuthController(FakeAuthPort(), false)
            .login(LoginRequest(loginId = "entry", password = "password123!"))

        assertTrue(response.headers[HttpHeaders.SET_COOKIE].orEmpty().none { it.contains("Secure") })

        val secureResponse = AuthController(FakeAuthPort(), true)
            .login(LoginRequest(loginId = "entry", password = "password123!"))

        assertTrue(secureResponse.headers[HttpHeaders.SET_COOKIE].orEmpty().all { it.contains("Secure") })
    }

    @Test
    fun logoutUsesValidatedPrincipalAndExpiresCookies() {
        val authPort = FakeAuthPort()
        val controller = AuthController(authPort)

        val response = controller.logout(
            UsernamePasswordAuthenticationToken(AuthenticatedUser(123L), null)
        )

        val command = requireNotNull(authPort.logoutCommand)
        val cookies = response.headers[HttpHeaders.SET_COOKIE].orEmpty()
        assertEquals(123L, command.userId)
        assertEquals(HttpStatus.OK, response.statusCode)
        assertTrue(cookies.any { it.contains("access_token=") && it.contains("Max-Age=0") })
        assertTrue(cookies.any { it.contains("refresh_token=") && it.contains("Max-Age=0") })
    }

    @Test(expected = IllegalArgumentException::class)
    fun logoutRejectsRawStringPrincipal() {
        val authPort = FakeAuthPort()
        val controller = AuthController(authPort)

        controller.logout(UsernamePasswordAuthenticationToken("user_123", null))
    }

    @Test
    fun refreshMapsCommandAndIssuesFreshCookies() {
        val authPort = FakeAuthPort()
        val controller = AuthController(authPort)

        val response = controller.refreshToken("refresh-token")

        assertEquals("refresh-token", authPort.refreshTokenCommand?.refreshToken)
        assertEquals("user_123", response.body?.data?.userId)
        assertEquals(2, response.headers[HttpHeaders.SET_COOKIE]?.size)
    }

    @Test
    fun passwordResetMapsAllSensitiveFieldsToCommand() {
        val authPort = FakeAuthPort()
        val controller = PasswordResetController(authPort)
        val birthdate = LocalDate.parse("2009-03-15")

        controller.resetPassword(
            hs.kr.entrydsm.identity.adapterin.web.dto.request.PasswordResetRequest(
                loginId = "entry",
                name = "홍길동",
                birthdate = birthdate,
                newPassword = "new-password",
            )
        )

        val command = requireNotNull(authPort.resetPasswordCommand)
        assertEquals("entry", command.loginId)
        assertEquals("홍길동", command.name)
        assertEquals(birthdate, command.birthdate)
        assertEquals("new-password", command.newPassword)
    }

    @Test
    fun passwordResetDelegatesOwnershipVerificationToApplicationService() {
        val authPort = FakeAuthPort()
        val controller = PasswordResetController(authPort)

        controller.resetPassword(
            hs.kr.entrydsm.identity.adapterin.web.dto.request.PasswordResetRequest(
                loginId = "entry",
                name = "홍길동",
                birthdate = LocalDate.parse("2009-03-15"),
                newPassword = "new-password",
            )
        )

        assertEquals("entry", requireNotNull(authPort.resetPasswordCommand).loginId)
    }

    @Test
    fun commandToStringsDoNotExposeCredentialsOrTokens() {
        assertFalse(LoginCommand("entry", "secret").toString().contains("secret"))
        assertFalse(RefreshTokenCommand("refresh-secret").toString().contains("refresh-secret"))
        assertFalse(
            PasswordResetCommand("entry", "홍길동", LocalDate.parse("2009-03-15"), "new-secret")
                .toString()
                .contains("new-secret")
        )
        assertFalse(
            SignupCommand(
                password = "secret",
                name = "홍길동",
                phone = "01012345678",
                birthdate = LocalDate.parse("2009-03-15"),
                signupType = SignupType.SELF,
            ).toString().contains("홍길동")
        )
    }

    private class FakeAuthPort : AuthPort {
        var signupCommand: SignupCommand? = null
        var loginCommand: LoginCommand? = null
        var logoutCommand: LogoutCommand? = null
        var refreshTokenCommand: RefreshTokenCommand? = null
        var resetPasswordCommand: PasswordResetCommand? = null

        override fun signup(command: SignupCommand): AccountResult {
            signupCommand = command
            return AccountResult(
                userId = 123L,
                role = Role.USER,
                status = AccountStatus.ACTIVE,
                profile = ProfileResult(
                    name = command.name,
                    phone = command.phone,
                    birthdate = command.birthdate,
                    signupType = command.signupType,
                    applicantStatus = ApplicantStatus.NONE,
                ),
                createdAt = NOW,
                updatedAt = NOW,
            )
        }

        override fun login(command: LoginCommand): AuthTokenResult {
            loginCommand = command
            return tokenResult()
        }

        override fun logout(command: LogoutCommand) {
            logoutCommand = command
        }

        override fun refreshToken(command: RefreshTokenCommand): AuthTokenResult {
            refreshTokenCommand = command
            return tokenResult()
        }

        override fun resetPassword(command: PasswordResetCommand) {
            resetPasswordCommand = command
        }

        private fun tokenResult(): AuthTokenResult {
            val generator = tokenGenerator()
            return AuthTokenResult(
                userId = 123L,
                role = Role.STUDENT,
                status = AccountStatus.ACTIVE,
                accessToken = generator.generateAccessToken("user_123"),
                refreshToken = generator.generateRefreshToken("user_123"),
            )
        }
    }

    private companion object {
        val NOW: Instant = Instant.parse("2026-06-11T10:00:00Z")

        fun tokenGenerator(): JwtTokenGenerator = JwtTokenGenerator(
            secret = "01234567890123456789012345678901",
            issuer = "entrydsm-identity",
            clock = Clock.fixed(NOW, ZoneOffset.UTC),
        )
    }
}
