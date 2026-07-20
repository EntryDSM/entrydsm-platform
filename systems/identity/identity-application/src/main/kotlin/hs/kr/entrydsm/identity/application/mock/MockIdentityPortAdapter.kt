package hs.kr.entrydsm.identity.application.mock

import hs.kr.entrydsm.identity.application.port.`in`.AccountPort
import hs.kr.entrydsm.identity.application.port.`in`.ApplicationPort
import hs.kr.entrydsm.identity.application.port.`in`.AuthPort
import hs.kr.entrydsm.identity.application.port.`in`.command.CancelApplicationCommand
import hs.kr.entrydsm.identity.application.port.`in`.command.DeleteAccountCommand
import hs.kr.entrydsm.identity.application.port.`in`.command.LoginCommand
import hs.kr.entrydsm.identity.application.port.`in`.command.LogoutCommand
import hs.kr.entrydsm.identity.application.port.`in`.command.PasswordResetCommand
import hs.kr.entrydsm.identity.application.port.`in`.command.ReadAccountCommand
import hs.kr.entrydsm.identity.application.port.`in`.command.ReadApplicationCommand
import hs.kr.entrydsm.identity.application.port.`in`.command.RefreshTokenCommand
import hs.kr.entrydsm.identity.application.port.`in`.command.SignupCommand
import hs.kr.entrydsm.identity.application.port.`in`.result.AccountResult
import hs.kr.entrydsm.identity.application.port.`in`.result.ApplicationResultResult
import hs.kr.entrydsm.identity.application.port.`in`.result.ApplicationStatusResult
import hs.kr.entrydsm.identity.application.port.`in`.result.BasicInfoResult
import hs.kr.entrydsm.identity.application.port.`in`.result.ProfileResult
import hs.kr.entrydsm.identity.application.port.`in`.result.UserSummaryResult
import hs.kr.entrydsm.identity.domain.enum.AccountStatus
import hs.kr.entrydsm.identity.domain.enum.ApplicantStatus
import hs.kr.entrydsm.identity.domain.enum.PassStatus
import hs.kr.entrydsm.identity.domain.enum.SignupType
import java.time.Instant
import java.time.LocalDate

class MockIdentityPortAdapter : AuthPort, AccountPort, ApplicationPort {
    private val now = Instant.parse("2026-06-11T10:00:00Z")

    override fun signup(command: SignupCommand): AccountResult =
        AccountResult(
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
            createdAt = now,
            updatedAt = now,
        )

    override fun login(command: LoginCommand): UserSummaryResult =
        UserSummaryResult(
            userId = 123L,
            role = "STUDENT",
            status = AccountStatus.ACTIVE,
        )

    override fun logout(command: LogoutCommand) = Unit

    override fun refreshToken(command: RefreshTokenCommand) = Unit

    override fun resetPassword(command: PasswordResetCommand) = Unit

    override fun deleteAccount(command: DeleteAccountCommand) = Unit

    override fun getBasicInfo(command: ReadAccountCommand): BasicInfoResult =
        BasicInfoResult(
            userId = 123L,
            role = "USER",
            status = AccountStatus.ACTIVE,
            name = "홍길동",
            phone = "01012345678",
            birthdate = LocalDate.parse("2009-03-15"),
            signupType = SignupType.SELF,
            applicantStatus = ApplicantStatus.SUBMITTED,
            createdAt = now,
            updatedAt = Instant.parse("2026-06-11T10:30:00Z"),
        )

    override fun getApplicationStatus(command: ReadApplicationCommand): ApplicationStatusResult =
        ApplicationStatusResult(
            applicantStatus = ApplicantStatus.SUBMITTED,
            submittedAt = now,
            updatedAt = Instant.parse("2026-06-11T10:30:00Z"),
        )

    override fun getApplicationResult(command: ReadApplicationCommand): ApplicationResultResult =
        ApplicationResultResult(
            passStatus = PassStatus.PASSED,
            announcedAt = now,
        )

    override fun cancelApplication(command: CancelApplicationCommand): ApplicationStatusResult =
        ApplicationStatusResult(
            applicantStatus = ApplicantStatus.CANCELED,
            submittedAt = now,
            updatedAt = Instant.parse("2026-06-11T10:30:00Z"),
        )
}
