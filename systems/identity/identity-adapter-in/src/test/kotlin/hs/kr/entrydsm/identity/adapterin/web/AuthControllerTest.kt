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
import hs.kr.entrydsm.identity.application.port.`in`.result.ProfileResult
import hs.kr.entrydsm.identity.application.port.`in`.result.UserSummaryResult
import hs.kr.entrydsm.identity.domain.enum.AccountStatus
import hs.kr.entrydsm.identity.domain.enum.ApplicantStatus
import hs.kr.entrydsm.identity.domain.enum.SignupType
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus

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
        assertTrue(cookies.any { it.contains("access_token=mock-access-token") && it.contains("HttpOnly") })
        assertTrue(cookies.any { it.contains("refresh_token=mock-refresh-token") && it.contains("HttpOnly") })
    }

    @Test
    fun logoutPassesAuthorizationAndExpiresCookies() {
        val authPort = FakeAuthPort()
        val controller = AuthController(authPort)

        val response = controller.logout("Bearer access-token")

        val command = requireNotNull(authPort.logoutCommand)
        val cookies = response.headers[HttpHeaders.SET_COOKIE].orEmpty()
        assertEquals("Bearer access-token", command.authorization)
        assertEquals(HttpStatus.OK, response.statusCode)
        assertTrue(cookies.any { it.contains("access_token=") && it.contains("Max-Age=0") })
        assertTrue(cookies.any { it.contains("refresh_token=") && it.contains("Max-Age=0") })
    }

    private class FakeAuthPort : AuthPort {
        var signupCommand: SignupCommand? = null
        var loginCommand: LoginCommand? = null
        var logoutCommand: LogoutCommand? = null

        override fun signup(command: SignupCommand): AccountResult {
            signupCommand = command
            return AccountResult(
                userId = 123L,
                role = "USER",
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

        override fun login(command: LoginCommand): UserSummaryResult {
            loginCommand = command
            return UserSummaryResult(userId = 123L, role = "STUDENT", status = AccountStatus.ACTIVE)
        }

        override fun logout(command: LogoutCommand) {
            logoutCommand = command
        }

        override fun refreshToken(command: RefreshTokenCommand) {
            assertNotNull(command)
        }

        override fun resetPassword(command: PasswordResetCommand) {
            assertNotNull(command)
        }
    }

    private companion object {
        val NOW: Instant = Instant.parse("2026-06-11T10:00:00Z")
    }
}
