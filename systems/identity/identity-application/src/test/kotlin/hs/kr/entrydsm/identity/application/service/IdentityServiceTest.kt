package hs.kr.entrydsm.identity.application.service

import hs.kr.entrydsm.identity.application.port.`in`.command.CancelApplicationCommand
import hs.kr.entrydsm.identity.application.port.`in`.command.LoginCommand
import hs.kr.entrydsm.identity.application.port.`in`.command.PasswordResetCommand
import hs.kr.entrydsm.identity.application.port.`in`.command.ReadApplicationCommand
import hs.kr.entrydsm.identity.application.port.`in`.command.SignupCommand
import hs.kr.entrydsm.identity.domain.enum.ApplicantStatus
import hs.kr.entrydsm.identity.domain.enum.ErrorCode
import hs.kr.entrydsm.identity.domain.enum.PassStatus
import hs.kr.entrydsm.identity.domain.enum.SignupType
import hs.kr.entrydsm.identity.domain.exception.IdentityDomainException
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IdentityServiceTest {
    @Test
    fun signupCreatesAccountAndApplicationSnapshot() {
        val (services, accounts, applications) = services()

        val result = services.auth.signup(
            SignupCommand("Password1!", "김학생", "01099999999", LocalDate.parse("2009-03-16"), SignupType.SELF)
        )

        assertTrue(result.userId > 123L)
        assertEquals("김학생", result.profile.name)
        assertEquals(result.userId, accounts.findByUserId(result.userId)?.userId)
        assertEquals(ApplicantStatus.NONE, applications.findByUserId(result.userId)?.applicantStatus)
    }

    @Test
    fun loginRejectsWrongPassword() {
        val (serviceBundle, _, _) = services()

        val exception = try {
            serviceBundle.auth.login(LoginCommand("01012345678", "wrong"))
            null
        } catch (error: IdentityDomainException) {
            error
        }

        assertEquals(ErrorCode.INVALID_CREDENTIALS, exception?.errorCode)
    }

    @Test
    fun passwordResetChangesPasswordAfterIdentityCheck() {
        val (serviceBundle, _, _) = services()

        serviceBundle.auth.resetPassword(
            PasswordResetCommand(
                loginId = "01012345678",
                name = "홍길동",
                birthdate = LocalDate.parse("2009-03-15"),
                newPassword = "NewPassword1!",
            )
        )

        assertEquals(123L, serviceBundle.auth.login(LoginCommand("01012345678", "NewPassword1!")).userId)
    }

    @Test
    fun applicationStatusAndCancellationUseMockApplicationPort() {
        val (serviceBundle, _, _) = services()

        val status = serviceBundle.application.getApplicationStatus(ReadApplicationCommand("Bearer access-token"))
        val canceled = serviceBundle.application.cancelApplication(CancelApplicationCommand("Bearer access-token", "개인 사유"))

        assertEquals(ApplicantStatus.SUBMITTED, status.applicantStatus)
        assertEquals(ApplicantStatus.CANCELED, canceled.applicantStatus)
    }

    @Test
    fun resultIsUnavailableBeforeAnnouncement() {
        val (services, _, applications) = services()
        applications.snapshots[123L] = applications.snapshots.getValue(123L).copy(
            passStatus = PassStatus.NOT_ANNOUNCED,
            announcedAt = null,
        )

        val exception = try {
            services.application.getApplicationResult(ReadApplicationCommand("Bearer access-token"))
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
            ServiceBundle(
                auth = AuthService(accounts, applications),
                application = ApplicationService(accounts, applications),
            ),
            accounts,
            applications,
        )
    }

}

private data class ServiceBundle(
    val auth: AuthService,
    val application: ApplicationService,
)
