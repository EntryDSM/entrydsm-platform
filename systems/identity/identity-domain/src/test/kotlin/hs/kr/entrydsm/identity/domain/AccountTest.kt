package hs.kr.entrydsm.identity.domain

import hs.kr.entrydsm.identity.domain.enum.AccountStatus
import hs.kr.entrydsm.identity.domain.enum.ApplicantStatus
import hs.kr.entrydsm.identity.domain.enum.SignupType
import hs.kr.entrydsm.identity.domain.exception.IdentityDomainException
import hs.kr.entrydsm.identity.domain.model.Account
import hs.kr.entrydsm.identity.domain.model.StudentProfile
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class AccountTest {
    @Test
    fun changesPasswordAndUpdatesTimestamp() {
        val account = account(applicantStatus = ApplicantStatus.NONE)
        val updatedAt = Instant.parse("2026-06-11T11:00:00Z")

        account.changePassword("NewPassword1!", updatedAt)

        assertEquals(true, account.matchesPassword("NewPassword1!"))
        assertEquals(updatedAt, account.updatedAt)
    }

    @Test(expected = IdentityDomainException::class)
    fun cannotDeleteAccountWithSubmittedApplication() {
        account(applicantStatus = ApplicantStatus.SUBMITTED).delete(Instant.now())
    }

    private fun account(applicantStatus: ApplicantStatus): Account {
        val now = Instant.parse("2026-06-11T10:00:00Z")
        return Account.create(
            userId = 123L,
            loginId = "01012345678",
            password = "Password1!",
            role = "USER",
            status = AccountStatus.ACTIVE,
            profile = StudentProfile(
                name = "홍길동",
                phone = "01012345678",
                birthdate = LocalDate.parse("2009-03-15"),
                signupType = SignupType.SELF,
                applicantStatus = applicantStatus,
                updatedAt = now,
            ),
            createdAt = now,
            updatedAt = now,
        )
    }
}
