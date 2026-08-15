package hs.kr.entrydsm.identity.application.service

import hs.kr.entrydsm.identity.application.port.`in`.command.CancelApplicationCommand
import hs.kr.entrydsm.identity.application.port.`in`.command.ReadApplicationCommand
import hs.kr.entrydsm.identity.domain.enum.ApplicantStatus
import hs.kr.entrydsm.identity.domain.enum.ErrorCode
import hs.kr.entrydsm.identity.domain.enum.PassStatus
import hs.kr.entrydsm.identity.domain.exception.IdentityDomainException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Test

class IdentityServiceTest {
    @Test
    fun applicationStatusAndCancellationPersistState() {
        val (serviceBundle, accounts, applications) = services()

        val status = serviceBundle.application.getApplicationStatus(ReadApplicationCommand("Bearer access-token", 123L))
        val canceled = serviceBundle.application.cancelApplication(
            CancelApplicationCommand("Bearer access-token", "개인 사유", 123L),
        )

        assertEquals(ApplicantStatus.SUBMITTED, status.applicantStatus)
        assertEquals(ApplicantStatus.CANCELED, canceled.applicantStatus)
        assertEquals(CANCELLATION_TIME, canceled.updatedAt)
        assertEquals(CANCELLATION_TIME, applications.snapshots.getValue(123L).updatedAt)
        assertEquals(ApplicantStatus.CANCELED, accounts.findByUserId(123L)?.profile?.applicantStatus)
        assertEquals(CANCELLATION_TIME, accounts.findByUserId(123L)?.profile?.updatedAt)
    }

    @Test
    fun cancellationRejectsMissingApplication() {
        val (serviceBundle, _, applications) = services()
        applications.snapshots.remove(123L)

        val exception = captureIdentityException {
            serviceBundle.application.cancelApplication(CancelApplicationCommand("Bearer access-token", null, 123L))
        }

        assertEquals(ErrorCode.USER_NOT_FOUND, exception.errorCode)
    }

    @Test
    fun cancellationRejectsApplicationThatIsNotSubmitted() {
        val (serviceBundle, _, applications) = services()
        applications.snapshots[123L] = applications.snapshots.getValue(123L).copy(
            applicantStatus = ApplicantStatus.CANCELED,
        )

        val exception = captureIdentityException {
            serviceBundle.application.cancelApplication(CancelApplicationCommand("Bearer access-token", null, 123L))
        }

        assertEquals(ErrorCode.APPLICATION_CANCEL_NOT_ALLOWED, exception.errorCode)
    }

    @Test
    fun applicationServiceRejectsAuthorizationHeaderWithoutAuthenticatedUser() {
        val (serviceBundle, _, _) = services()

        val exception = captureIdentityException {
            serviceBundle.application.getApplicationStatus(ReadApplicationCommand("Bearer access-token"))
        }

        assertEquals(ErrorCode.AUTH_UNAUTHORIZED, exception.errorCode)
    }

    @Test
    fun resultIsUnavailableBeforeAnnouncement() {
        val (services, _, applications) = services()
        applications.snapshots[123L] = applications.snapshots.getValue(123L).copy(
            passStatus = PassStatus.NOT_ANNOUNCED,
            announcedAt = null,
        )

        val exception = try {
            services.application.getApplicationResult(ReadApplicationCommand("Bearer access-token", 123L))
            null
        } catch (error: IdentityDomainException) {
            error
        }

        assertEquals(ErrorCode.APPLICATION_RESULT_NOT_AVAILABLE, exception?.errorCode)
    }

    private fun services(): Triple<ServiceBundle, FakeAccountRepository, FakeApplicationDataPort> {
        val accounts = FakeAccountRepository()
        val applications = FakeApplicationDataPort()
        return Triple(
            ServiceBundle(application = ApplicationService(accounts, applications, FIXED_CLOCK)),
            accounts,
            applications,
        )
    }

    private fun captureIdentityException(block: () -> Unit): IdentityDomainException = try {
        block()
        error("Expected IdentityDomainException")
    } catch (exception: IdentityDomainException) {
        exception
    }

    private companion object {
        val CANCELLATION_TIME: Instant = Instant.parse("2026-06-11T11:00:00Z")
        val FIXED_CLOCK: Clock = Clock.fixed(CANCELLATION_TIME, ZoneOffset.UTC)
    }
}

private data class ServiceBundle(
    val application: ApplicationService,
)
