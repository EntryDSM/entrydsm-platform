package hs.kr.entrydsm.identity.adapterin.web

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
    fun getStatusMapsAuthorizationUserIdAndResponse() {
        val applicationPort = FakeApplicationPort()
        val controller = ApplicationController(applicationPort)

        val response = controller.getStatus(
            authenticatedUser = AuthenticatedUser(123L),
        )

        val command = requireNotNull(applicationPort.statusApplicationCommand)
        assertEquals(123L, command.userId)
        assertEquals(ApplicantStatus.SUBMITTED, response.data?.applicantStatus)
        assertEquals(NOW, response.data?.submittedAt)
        assertEquals(NOW, response.data?.updatedAt)
    }

    @Test
    fun getResultMapsAuthorizationUserIdAndResponse() {
        val applicationPort = FakeApplicationPort()
        val controller = ApplicationController(applicationPort)

        val response = controller.getResult(
            authenticatedUser = AuthenticatedUser(123L),
        )

        val command = requireNotNull(applicationPort.resultApplicationCommand)
        assertEquals(123L, command.userId)
        assertEquals("FIRST_PASSED", response.data?.passStatus)
        assertEquals("1차 전형 합격", response.data?.passDescription)
        assertEquals(NOW, response.data?.announcedAt)
    }

    private class FakeApplicationPort : ApplicationPort {
        var statusApplicationCommand: ReadApplicationCommand? = null
        var resultApplicationCommand: ReadApplicationCommand? = null

        override fun getApplicationStatus(command: ReadApplicationCommand): ApplicationStatusResult {
            statusApplicationCommand = command
            return applicationStatusResult(ApplicantStatus.SUBMITTED)
        }

        override fun getApplicationResult(command: ReadApplicationCommand): ApplicationResultResult {
            resultApplicationCommand = command
            return ApplicationResultResult(passStatus = PassStatus.FIRST_PASSED, announcedAt = NOW, applicationNumber = "0006")
        }

        override fun cancelApplication(command: CancelApplicationCommand): ApplicationStatusResult {
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
