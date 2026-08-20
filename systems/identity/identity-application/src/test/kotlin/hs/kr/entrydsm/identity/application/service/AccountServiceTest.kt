package hs.kr.entrydsm.identity.application.service

import hs.kr.entrydsm.identity.application.port.`in`.command.DeleteAccountCommand
import hs.kr.entrydsm.identity.application.port.`in`.command.ReadAccountCommand
import hs.kr.entrydsm.identity.application.port.out.AccountCommandPort
import hs.kr.entrydsm.identity.application.port.out.AccountQueryPort
import hs.kr.entrydsm.identity.application.port.out.AccountRegistration
import hs.kr.entrydsm.identity.application.port.out.ApplicationDataPort
import hs.kr.entrydsm.identity.application.port.out.data.ApplicationSnapshot
import hs.kr.entrydsm.identity.domain.enum.AccountStatus
import hs.kr.entrydsm.identity.domain.enum.ApplicantStatus
import hs.kr.entrydsm.identity.domain.enum.PassStatus
import hs.kr.entrydsm.identity.domain.enum.Role
import hs.kr.entrydsm.identity.domain.enum.SignupType
import hs.kr.entrydsm.identity.domain.exception.IdentityDomainException
import hs.kr.entrydsm.identity.domain.model.Account
import hs.kr.entrydsm.identity.domain.model.PasswordHash
import hs.kr.entrydsm.identity.domain.model.StudentProfile
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class AccountServiceTest {
    private val fixedClock = Clock.fixed(NOW, java.time.ZoneOffset.UTC)

    @Test
    fun deleteAccountUsesAuthenticatedUserAndPersistsDeletionTime() {
        val queryPort = FakeAccountQueryPort(account())
        val commandPort = FakeAccountCommandPort()
        val service = AccountService(queryPort, commandPort, FakeApplicationDataPort(), fixedClock)

        service.deleteAccount(DeleteAccountCommand("Bearer access-token", USER_ID))

        val saved = requireNotNull(commandPort.savedAccount)
        assertEquals(AccountStatus.DELETED, saved.status)
        assertEquals(NOW, saved.updatedAt)
    }

    @Test
    fun getBasicInfoMapsAccountAndApplicationFields() {
        val account = account()
        val application = ApplicationSnapshot(
            userId = USER_ID,
            applicantStatus = ApplicantStatus.SUBMITTED,
            submittedAt = SUBMITTED_AT,
            updatedAt = APPLICATION_UPDATED_AT,
            passStatus = PassStatus.PASSED,
            announcedAt = ANNOUNCED_AT,
        )
        val service = AccountService(
            FakeAccountQueryPort(account),
            FakeAccountCommandPort(),
            FakeApplicationDataPort(application),
            fixedClock,
        )

        val result = service.getBasicInfo(ReadAccountCommand("Bearer access-token", USER_ID))

        assertEquals(USER_ID, result.userId)
        assertEquals(Role.STUDENT, result.role)
        assertEquals(AccountStatus.ACTIVE, result.status)
        assertEquals("홍길동", result.name)
        assertEquals("01012345678", result.phone)
        assertEquals(LocalDate.of(2009, 3, 15), result.birthdate)
        assertEquals(SignupType.SELF, result.signupType)
        assertEquals(ApplicantStatus.SUBMITTED, result.applicantStatus)
        assertEquals(CREATED_AT, result.createdAt)
        assertEquals(APPLICATION_UPDATED_AT, result.updatedAt)
    }

    @Test(expected = IdentityDomainException::class)
    fun missingAuthenticatedUserIsRejected() {
        AccountService(
            FakeAccountQueryPort(account()),
            FakeAccountCommandPort(),
            FakeApplicationDataPort(),
            fixedClock,
        ).getBasicInfo(ReadAccountCommand(null))
    }

    private fun account(): Account = Account.create(
        userId = USER_ID,
        loginId = "01012345678",
        passwordHash = PasswordHash.fromEncoded("encoded-password"),
        role = Role.STUDENT,
        status = AccountStatus.ACTIVE,
        profile = StudentProfile(
            name = "홍길동",
            phone = "01012345678",
            birthdate = LocalDate.of(2009, 3, 15),
            signupType = SignupType.SELF,
            applicantStatus = ApplicantStatus.NONE,
            passStatus = PassStatus.NOT_ANNOUNCED,
            updatedAt = CREATED_AT,
        ),
        createdAt = CREATED_AT,
        updatedAt = CREATED_AT,
    )

    private class FakeAccountQueryPort(
        private val account: Account?,
    ) : AccountQueryPort {
        override fun findByLoginId(loginId: String): Account? = account

        override fun findByUserId(userId: Long): Account? = account?.takeIf { it.userId == userId }
    }

    private class FakeAccountCommandPort : AccountCommandPort {
        var savedAccount: Account? = null

        override fun save(account: Account): Account {
            savedAccount = account
            return account
        }

        override fun register(registration: AccountRegistration, createdAt: Instant): Account =
            error("Not used in AccountServiceTest")
    }

    private class FakeApplicationDataPort(
        private val snapshot: ApplicationSnapshot = ApplicationSnapshot(
            userId = USER_ID,
            applicantStatus = ApplicantStatus.NONE,
            submittedAt = null,
            updatedAt = CREATED_AT,
            passStatus = PassStatus.NOT_ANNOUNCED,
            announcedAt = null,
        ),
    ) : ApplicationDataPort {
        override fun create(userId: Long, updatedAt: Instant): ApplicationSnapshot = snapshot

        override fun findByUserId(userId: Long): ApplicationSnapshot? = snapshot.takeIf { it.userId == userId }

        override fun cancel(userId: Long, reason: String?, updatedAt: Instant): ApplicationSnapshot = snapshot
    }

    private companion object {
        const val USER_ID = 123L
        val CREATED_AT: Instant = Instant.parse("2026-06-11T10:00:00Z")
        val NOW: Instant = Instant.parse("2026-06-11T11:00:00Z")
        val SUBMITTED_AT: Instant = Instant.parse("2026-06-11T10:30:00Z")
        val APPLICATION_UPDATED_AT: Instant = Instant.parse("2026-06-11T10:45:00Z")
        val ANNOUNCED_AT: Instant = Instant.parse("2026-06-11T10:40:00Z")
    }
}
