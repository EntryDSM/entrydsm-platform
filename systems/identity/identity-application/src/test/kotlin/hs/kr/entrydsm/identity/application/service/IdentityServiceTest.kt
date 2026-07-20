package hs.kr.entrydsm.identity.application.service

import hs.kr.entrydsm.identity.application.port.`in`.command.CancelApplicationCommand
import hs.kr.entrydsm.identity.application.port.`in`.command.LoginCommand
import hs.kr.entrydsm.identity.application.port.`in`.command.PasswordResetCommand
import hs.kr.entrydsm.identity.application.port.`in`.command.ReadApplicationCommand
import hs.kr.entrydsm.identity.application.port.`in`.command.SignupCommand
import hs.kr.entrydsm.identity.domain.ApplicantStatus
import hs.kr.entrydsm.identity.domain.ErrorCode
import hs.kr.entrydsm.identity.domain.PassStatus
import hs.kr.entrydsm.identity.domain.SignupType
import hs.kr.entrydsm.identity.domain.exception.IdentityDomainException
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IdentityServiceTest {
    @Test
    fun signupCreatesAccountAndApplicationSnapshot() {
        val (service, accounts, applications) = service()

        val result = service.signup(
            SignupCommand("Password1!", "김학생", "01099999999", LocalDate.parse("2009-03-16"), SignupType.SELF)
        )

        assertTrue(result.userId > 123L)
        assertEquals("김학생", result.profile.name)
        assertEquals(result.userId, accounts.findByUserId(result.userId)?.userId)
        assertEquals(ApplicantStatus.NONE, applications.findByUserId(result.userId)?.applicantStatus)
    }

    @Test
    fun loginRejectsWrongPassword() {
        val (service) = service()

        val exception = try {
            service.login(LoginCommand("01012345678", "wrong"))
            null
        } catch (error: IdentityDomainException) {
            error
        }

        assertEquals(ErrorCode.INVALID_CREDENTIALS, exception?.errorCode)
    }

    @Test
    fun passwordResetChangesPasswordAfterIdentityCheck() {
        val (service) = service()

        service.resetPassword(
            PasswordResetCommand(
                loginId = "01012345678",
                name = "홍길동",
                birthdate = LocalDate.parse("2009-03-15"),
                newPassword = "NewPassword1!",
            )
        )

        assertEquals(123L, service.login(LoginCommand("01012345678", "NewPassword1!")).userId)
    }

    @Test
    fun applicationStatusAndCancellationUseMockApplicationPort() {
        val (service) = service()

        val status = service.getApplicationStatus(ReadApplicationCommand("Bearer access-token"))
        val canceled = service.cancelApplication(CancelApplicationCommand("Bearer access-token", "개인 사유"))

        assertEquals(ApplicantStatus.SUBMITTED, status.applicantStatus)
        assertEquals(ApplicantStatus.CANCELED, canceled.applicantStatus)
    }

    @Test
    fun resultIsUnavailableBeforeAnnouncement() {
        val (service, _, applications) = service()
        applications.snapshots[123L] = applications.snapshots.getValue(123L).copy(
            passStatus = PassStatus.NOT_ANNOUNCED,
            announcedAt = null,
        )

        val exception = try {
            service.getApplicationResult(ReadApplicationCommand("Bearer access-token"))
            null
        } catch (error: IdentityDomainException) {
            error
        }

        assertEquals(ErrorCode.APPLICATION_RESULT_NOT_AVAILABLE, exception?.errorCode)
    }

    private fun service(): Triple<IdentityService, FakeAccountRepository, FakeApplicationDataPort> {
        val accounts = FakeAccountRepository()
        val applications = FakeApplicationDataPort()
        return Triple(IdentityService(accounts, applications), accounts, applications)
    }

}
