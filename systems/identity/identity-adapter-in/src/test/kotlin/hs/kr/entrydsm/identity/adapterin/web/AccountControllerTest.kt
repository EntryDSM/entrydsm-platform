package hs.kr.entrydsm.identity.adapterin.web

import hs.kr.entrydsm.identity.application.port.`in`.AccountPort
import hs.kr.entrydsm.identity.application.port.`in`.command.DeleteAccountCommand
import hs.kr.entrydsm.identity.application.port.`in`.command.ReadAccountCommand
import hs.kr.entrydsm.identity.application.port.`in`.result.BasicInfoResult
import hs.kr.entrydsm.identity.application.port.`in`.result.UserSummaryResult
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
    fun getMePassesAuthorizationAndReturnsBasicInfo() {
        val accountPort = FakeAccountPort()
        val controller = AccountController(accountPort)

        val response = controller.getMe("Bearer access-token")

        val command = requireNotNull(accountPort.readAccountCommand)
        assertEquals("Bearer access-token", command.authorization)
        assertEquals("user_123", response.data?.userId)
        assertEquals(ApplicantStatus.SUBMITTED, response.data?.applicantStatus)
    }

    @Test
    fun getMyAuthorityReturnsRoleWithoutPersonalData() {
        val accountPort = FakeAccountPort()
        val controller = AccountController(accountPort)

        val response = controller.getMyAuthority("Bearer access-token")

        val command = requireNotNull(accountPort.authorityCommand)
        assertEquals("Bearer access-token", command.authorization)
        assertEquals("user_123", response.data?.userId)
        assertEquals("STUDENT", response.data?.role)
        assertEquals(AccountStatus.ACTIVE, response.data?.status)
    }

    @Test
    fun deleteMePassesAuthorizationToAccountPort() {
        val accountPort = FakeAccountPort()
        val controller = AccountController(accountPort)

        val response = controller.deleteMe("Bearer access-token")

        assertNull(response.data)
        assertEquals("Bearer access-token", requireNotNull(accountPort.deleteAccountCommand).authorization)
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
