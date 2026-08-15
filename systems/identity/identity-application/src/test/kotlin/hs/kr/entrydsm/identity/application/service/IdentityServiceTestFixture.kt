package hs.kr.entrydsm.identity.application.service

import hs.kr.entrydsm.identity.application.port.out.AccountRepository
import hs.kr.entrydsm.identity.application.port.out.AccountRegistration
import hs.kr.entrydsm.identity.application.port.out.ApplicationDataPort
import hs.kr.entrydsm.identity.application.port.out.data.ApplicationSnapshot
import hs.kr.entrydsm.identity.domain.enum.AccountStatus
import hs.kr.entrydsm.identity.domain.enum.ApplicantStatus
import hs.kr.entrydsm.identity.domain.enum.PassStatus
import hs.kr.entrydsm.identity.domain.enum.SignupType
import hs.kr.entrydsm.identity.domain.enum.ErrorCode
import hs.kr.entrydsm.identity.domain.enum.Role
import hs.kr.entrydsm.identity.domain.exception.IdentityDomainException
import hs.kr.entrydsm.identity.domain.model.Account
import hs.kr.entrydsm.identity.domain.model.PasswordHash
import hs.kr.entrydsm.identity.domain.model.StudentProfile
import java.time.Instant
import java.time.LocalDate

internal class FakeAccountRepository : AccountRepository {
    val accounts = mutableMapOf(123L to sampleAccount())

    override fun findByLoginId(loginId: String): Account? = accounts.values.firstOrNull { it.loginId == loginId }

    override fun findByUserId(userId: Long): Account? = accounts[userId]

    override fun save(account: Account): Account = account.also { accounts[it.userId] = it }

    override fun register(registration: AccountRegistration, createdAt: Instant): Account =
        Account.create(
            userId = (accounts.keys.maxOrNull() ?: 0L) + 1L,
            loginId = registration.loginId,
            passwordHash = registration.passwordHash,
            role = registration.role,
            status = registration.status,
            profile = registration.profile,
            createdAt = createdAt,
            updatedAt = createdAt,
        ).also(::save)
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

    override fun cancel(userId: Long, reason: String?, updatedAt: Instant): ApplicationSnapshot {
        val current = snapshots[userId] ?: throw IdentityDomainException(ErrorCode.USER_NOT_FOUND)
        if (current.applicantStatus != ApplicantStatus.SUBMITTED) {
            throw IdentityDomainException(ErrorCode.APPLICATION_CANCEL_NOT_ALLOWED)
        }
        return current.copy(applicantStatus = ApplicantStatus.CANCELED, updatedAt = updatedAt)
            .also { snapshots[userId] = it }
    }
}

private fun sampleAccount(): Account = Account.create(
    userId = 123L,
    loginId = "01012345678",
    passwordHash = PasswordHash.fromEncoded("\$2a\$10\$test-password-hash"),
    role = Role.USER,
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
