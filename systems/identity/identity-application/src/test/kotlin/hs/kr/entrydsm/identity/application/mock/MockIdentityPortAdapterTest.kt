package hs.kr.entrydsm.identity.application.mock

import hs.kr.entrydsm.identity.application.port.`in`.command.CancelApplicationCommand
import hs.kr.entrydsm.identity.application.port.`in`.command.LoginCommand
import hs.kr.entrydsm.identity.application.port.`in`.command.ReadAccountCommand
import hs.kr.entrydsm.identity.application.port.`in`.command.ReadApplicationCommand
import hs.kr.entrydsm.identity.application.port.`in`.command.SignupCommand
import hs.kr.entrydsm.identity.domain.AccountStatus
import hs.kr.entrydsm.identity.domain.ApplicantStatus
import hs.kr.entrydsm.identity.domain.PassStatus
import hs.kr.entrydsm.identity.domain.SignupType
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class MockIdentityPortAdapterTest {
    private val adapter = MockIdentityPortAdapter()

    @Test
    fun signupReturnsAccountResultFromCommandProfile() {
        val birthdate = LocalDate.parse("2009-03-15")

        val result = adapter.signup(
            SignupCommand(
                password = "password123!",
                name = "홍길동",
                phone = "01012345678",
                birthdate = birthdate,
                signupType = SignupType.SELF,
            )
        )

        assertEquals(123L, result.userId)
        assertEquals(AccountStatus.ACTIVE, result.status)
        assertEquals("홍길동", result.profile.name)
        assertEquals("01012345678", result.profile.phone)
        assertEquals(birthdate, result.profile.birthdate)
        assertEquals(SignupType.SELF, result.profile.signupType)
    }

    @Test
    fun loginReturnsActiveStudentSummary() {
        val result = adapter.login(LoginCommand(loginId = "entry", password = "password123!"))

        assertEquals(123L, result.userId)
        assertEquals("STUDENT", result.role)
        assertEquals(AccountStatus.ACTIVE, result.status)
    }

    @Test
    fun getBasicInfoReturnsSubmittedApplicant() {
        val result = adapter.getBasicInfo(ReadAccountCommand(authorization = "Bearer access-token"))

        assertEquals(123L, result.userId)
        assertEquals("홍길동", result.name)
        assertEquals(ApplicantStatus.SUBMITTED, result.applicantStatus)
    }

    @Test
    fun applicationResultAndCancellationReturnExpectedStatuses() {
        val application = adapter.getApplicationResult(ReadApplicationCommand("Bearer access-token"))
        val canceled = adapter.cancelApplication(CancelApplicationCommand("Bearer access-token", "reason"))

        assertEquals(PassStatus.PASSED, application.passStatus)
        assertEquals(ApplicantStatus.CANCELED, canceled.applicantStatus)
    }
}
