package hs.kr.entrydsm.identity.application.service

import hs.kr.entrydsm.identity.application.port.out.AccountRepository
import hs.kr.entrydsm.identity.application.port.out.ApplicationDataPort
import hs.kr.entrydsm.identity.application.port.out.data.ApplicationSnapshot
import hs.kr.entrydsm.identity.domain.enum.AccountStatus
import hs.kr.entrydsm.identity.domain.enum.ApplicantStatus
import hs.kr.entrydsm.identity.domain.enum.PassStatus
import hs.kr.entrydsm.identity.domain.enum.SignupType
import hs.kr.entrydsm.identity.domain.model.Account
import hs.kr.entrydsm.identity.domain.model.StudentProfile
import java.time.Instant
import java.time.LocalDate

internal class FakeAccountRepository : AccountRepository {
    val accounts = mutableMapOf(123L to sampleAccount())

    override fun findByLoginId(loginId: String): Account? = accounts.values.firstOrNull { it.loginId == loginId }

    override fun findByUserId(userId: Long): Account? = accounts[userId]

    override fun save(account: Account): Account = account.also { accounts[it.userId] = it }
}

internal class FakeApplicationDataPort : ApplicationDataPort {
    val snapshots = mutableMapOf(123L to sampleSnapshot())

    override fun create(userId: Long, updatedAt: Instant): ApplicationSnapshot =
        sampleSnapshot().copy(
            userId = userId,
            applicantStatus = ApplicantStatus.NONE,
            submittedAt = null,
            passStatus = PassStatus.NOT_ANNOUNCED,
            announcedAt = null,
            updatedAt = updatedAt,
        ).also { snapshots[userId] = it }

    override fun findByUserId(userId: Long): ApplicationSnapshot? = snapshots[userId]

    override fun cancel(userId: Long, reason: String?, updatedAt: Instant): ApplicationSnapshot =
        snapshots.getValue(userId).copy(applicantStatus = ApplicantStatus.CANCELED, updatedAt = updatedAt)
            .also { snapshots[userId] = it }
}

private fun sampleAccount(): Account = Account.create(
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
        applicantStatus = ApplicantStatus.SUBMITTED,
        updatedAt = Instant.parse("2026-06-11T10:00:00Z"),
    ),
    createdAt = Instant.parse("2026-06-11T10:00:00Z"),
    updatedAt = Instant.parse("2026-06-11T10:00:00Z"),
)

private fun sampleSnapshot(): ApplicationSnapshot = ApplicationSnapshot(
    userId = 123L,
    applicantStatus = ApplicantStatus.SUBMITTED,
    submittedAt = Instant.parse("2026-06-11T10:00:00Z"),
    updatedAt = Instant.parse("2026-06-11T10:30:00Z"),
    passStatus = PassStatus.PASSED,
    announcedAt = Instant.parse("2026-06-11T10:00:00Z"),
)
