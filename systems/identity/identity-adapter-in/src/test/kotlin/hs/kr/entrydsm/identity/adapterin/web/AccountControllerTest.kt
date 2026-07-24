package hs.kr.entrydsm.identity.adapterin.web

import hs.kr.entrydsm.identity.application.port.`in`.AccountPort
import hs.kr.entrydsm.identity.application.port.`in`.command.DeleteAccountCommand
import hs.kr.entrydsm.identity.application.port.`in`.command.ReadAccountCommand
import hs.kr.entrydsm.identity.application.port.`in`.result.BasicInfoResult
import hs.kr.entrydsm.identity.domain.enum.AccountStatus
import hs.kr.entrydsm.identity.domain.enum.ApplicantStatus
import hs.kr.entrydsm.identity.domain.enum.SignupType
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
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

    private class FakeAccountPort : AccountPort {
        var readAccountCommand: ReadAccountCommand? = null

        override fun deleteAccount(command: DeleteAccountCommand) = Unit

        override fun getBasicInfo(command: ReadAccountCommand): BasicInfoResult {
            readAccountCommand = command
            return BasicInfoResult(
                userId = 123L,
                role = "USER",
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
    }

    private companion object {
        val NOW: Instant = Instant.parse("2026-06-11T10:00:00Z")
    }
}
