package hs.kr.entrydsm.identity.application.service

import hs.kr.entrydsm.identity.application.port.out.AccountRepository
import hs.kr.entrydsm.identity.application.port.out.ApplicationDataPort
import hs.kr.entrydsm.identity.application.port.out.data.ApplicationSnapshot
import hs.kr.entrydsm.identity.domain.Account
import hs.kr.entrydsm.identity.domain.AccountStatus
import hs.kr.entrydsm.identity.domain.ApplicantStatus
import hs.kr.entrydsm.identity.domain.PassStatus
import hs.kr.entrydsm.identity.domain.SignupType
import hs.kr.entrydsm.identity.domain.StudentProfile
import java.time.Instant
import java.time.LocalDate

internal class FakeAccountRepository : AccountRepository {
    val accounts = mutableMapOf("user_123" to sampleAccount())

    override fun findByLoginId(loginId: String): Account? = accounts.values.firstOrNull { it.loginId == loginId }

    override fun findByUserId(userId: String): Account? = accounts[userId]

    override fun save(account: Account): Account = account.also { accounts[it.userId] = it }
}

internal class FakeApplicationDataPort : ApplicationDataPort {
    val snapshots = mutableMapOf("user_123" to sampleSnapshot())

    override fun create(userId: String, updatedAt: Instant): ApplicationSnapshot =
        sampleSnapshot().copy(
            userId = userId,
            applicantStatus = ApplicantStatus.NONE,
            submittedAt = null,
            passStatus = PassStatus.NOT_ANNOUNCED,
            announcedAt = null,
            updatedAt = updatedAt,
        ).also { snapshots[userId] = it }

    override fun findByUserId(userId: String): ApplicationSnapshot? = snapshots[userId]

    override fun cancel(userId: String, reason: String?, updatedAt: Instant): ApplicationSnapshot =
        snapshots.getValue(userId).copy(applicantStatus = ApplicantStatus.CANCELED, updatedAt = updatedAt)
            .also { snapshots[userId] = it }
}

private fun sampleAccount(): Account = Account.create(
    userId = "user_123",
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
    userId = "user_123",
    applicantStatus = ApplicantStatus.SUBMITTED,
    submittedAt = Instant.parse("2026-06-11T10:00:00Z"),
    updatedAt = Instant.parse("2026-06-11T10:30:00Z"),
    passStatus = PassStatus.PASSED,
    announcedAt = Instant.parse("2026-06-11T10:00:00Z"),
)
