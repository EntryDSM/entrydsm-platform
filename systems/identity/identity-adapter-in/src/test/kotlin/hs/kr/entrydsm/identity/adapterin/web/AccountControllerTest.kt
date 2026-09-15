package hs.kr.entrydsm.identity.adapterin.web

import hs.kr.entrydsm.identity.application.port.`in`.AccountPort
import hs.kr.entrydsm.identity.application.port.`in`.command.DeleteAccountCommand
import hs.kr.entrydsm.identity.application.port.`in`.command.ReadAccountCommand
import hs.kr.entrydsm.identity.application.port.`in`.result.BasicInfoResult
import hs.kr.entrydsm.identity.application.port.`in`.result.UserSummaryResult
import hs.kr.entrydsm.identity.application.security.AuthenticatedUser
import hs.kr.entrydsm.identity.domain.enum.AccountStatus
import hs.kr.entrydsm.identity.domain.enum.ApplicantStatus
import hs.kr.entrydsm.identity.domain.enum.Role
import hs.kr.entrydsm.identity.domain.enum.SignupType
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AccountControllerTest {
    @Test
    fun getMePassesAuthenticatedUserIdAndReturnsBasicInfo() {
        val accountPort = FakeAccountPort()
        val controller = AccountController(accountPort)

        val authenticatedUser = AuthenticatedUser(
            userId = 123L,
        )

        val response = controller.getMe(authenticatedUser)

        val command = requireNotNull(accountPort.readAccountCommand)
        assertEquals(123L, command.userId)

        assertEquals("user_123", response.data?.userId)
        assertEquals(ApplicantStatus.SUBMITTED, response.data?.applicantStatus)
    }

    @Test
    fun getMyAuthorityReturnsRoleWithoutPersonalData() {
        val accountPort = FakeAccountPort()
        val controller = AccountController(accountPort)

        val authenticatedUser = AuthenticatedUser(
            userId = 123L,
        )

        val response = controller.getMyAuthority(authenticatedUser)

        val command = requireNotNull(accountPort.authorityCommand)
        assertEquals(123L, command.userId)
        assertEquals("user_123", response.data?.userId)
        assertEquals("STUDENT", response.data?.role)
        assertEquals(AccountStatus.ACTIVE, response.data?.status)
    }

    @Test
    fun deleteMePassesAuthorizationToAccountPort() {
        val accountPort = FakeAccountPort()
        val controller = AccountController(accountPort)

        val authenticatedUser = AuthenticatedUser(
            userId = 123L,
        )

        val response = controller.getMyAuthority(authenticatedUser)

        assertNull(response.data)
        assertEquals(123L, requireNotNull(accountPort.deleteAccountCommand).userId)
    }

    private class FakeAccountPort : AccountPort {
        var readAccountCommand: ReadAccountCommand? = null
        var deleteAccountCommand: DeleteAccountCommand? = null
        var authorityCommand: ReadAccountCommand? = null

        override fun deleteAccount(command: DeleteAccountCommand) {
            deleteAccountCommand = command
        }

        override fun getBasicInfo(command: ReadAccountCommand): BasicInfoResult {
            readAccountCommand = command
            return BasicInfoResult(
                userId = 123L,
                role = Role.USER,
                status = AccountStatus.ACTIVE,
                name = "홍길동",
                phone = "01012345678",
                birthdate = LocalDate.parse("2009-03-15"),
                signupType = SignupType.SELF,
                applicantStatus = ApplicantStatus.SUBMITTED,
                createdAt = NOW,
                updatedAt = NOW,
            )
        }

        override fun getAuthority(command: ReadAccountCommand): UserSummaryResult {
            authorityCommand = command
            return UserSummaryResult(
                userId = 123L,
                role = Role.STUDENT,
                status = AccountStatus.ACTIVE,
            )
        }
    }

    private companion object {
        val NOW: Instant = Instant.parse("2026-06-11T10:00:00Z")
    }
}
