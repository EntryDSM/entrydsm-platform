package hs.kr.entrydsm.identity.adapterin.web

import hs.kr.entrydsm.identity.adapterin.web.dto.request.ApplicationCancelRequest
import hs.kr.entrydsm.identity.application.port.`in`.ApplicationPort
import hs.kr.entrydsm.identity.application.port.`in`.command.CancelApplicationCommand
import hs.kr.entrydsm.identity.application.port.`in`.command.ReadApplicationCommand
import hs.kr.entrydsm.identity.application.port.`in`.result.ApplicationResultResult
import hs.kr.entrydsm.identity.application.port.`in`.result.ApplicationStatusResult
import hs.kr.entrydsm.identity.application.security.AuthenticatedUser
import hs.kr.entrydsm.identity.domain.enum.ApplicantStatus
import hs.kr.entrydsm.identity.domain.enum.PassStatus
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class ApplicationControllerTest {
    @Test
    fun cancelApplicationMapsAuthorizationAndReason() {
        val applicationPort = FakeApplicationPort()
        val controller = ApplicationController(applicationPort)

        val response = controller.cancel(
            authorization = "Bearer access-token",
            request = ApplicationCancelRequest(reason = "change of plan"),
            authenticatedUser = AuthenticatedUser(123L),
        )

        val command = requireNotNull(applicationPort.cancelApplicationCommand)
        assertEquals("Bearer access-token", command.authorization)
        assertEquals("change of plan", command.reason)
        assertEquals(123L, command.userId)
        assertEquals(ApplicantStatus.CANCELED, response.data?.applicantStatus)
    }

    private class FakeApplicationPort : ApplicationPort {
        var cancelApplicationCommand: CancelApplicationCommand? = null

        override fun getApplicationStatus(command: ReadApplicationCommand): ApplicationStatusResult =
            applicationStatusResult(ApplicantStatus.SUBMITTED)

        override fun getApplicationResult(command: ReadApplicationCommand): ApplicationResultResult =
            ApplicationResultResult(passStatus = PassStatus.PASSED, announcedAt = NOW)

        override fun cancelApplication(command: CancelApplicationCommand): ApplicationStatusResult {
            cancelApplicationCommand = command
            return applicationStatusResult(ApplicantStatus.CANCELED)
        }
    }

    private companion object {
        val NOW: Instant = Instant.parse("2026-06-11T10:00:00Z")

        fun applicationStatusResult(applicantStatus: ApplicantStatus): ApplicationStatusResult =
            ApplicationStatusResult(
                applicantStatus = applicantStatus,
                submittedAt = NOW,
                updatedAt = NOW,
            )
    }
}
