package hs.kr.entrydsm.identity.domain.model

import hs.kr.entrydsm.identity.domain.enum.AccountStatus
import hs.kr.entrydsm.identity.domain.enum.ApplicantStatus
import hs.kr.entrydsm.identity.domain.enum.ErrorCode
import hs.kr.entrydsm.identity.domain.enum.Role
import hs.kr.entrydsm.identity.domain.exception.IdentityDomainException
import java.time.Instant

class Account(
    val userId: Long,
    val loginId: String,
    passwordHash: PasswordHash,
    val role: Role,
    status: AccountStatus,
    val profile: StudentProfile,
    val createdAt: Instant,
    updatedAt: Instant,
) {
    var passwordHash: PasswordHash = passwordHash
        private set

    var status: AccountStatus = status
        private set

    var updatedAt: Instant = updatedAt
        private set

    fun changePassword(newPasswordHash: PasswordHash, now: Instant) {
        if (passwordHash == newPasswordHash) {
            throw IdentityDomainException(ErrorCode.PASSWORD_SAME_AS_OLD)
        }
        passwordHash = newPasswordHash
        updatedAt = now
    }

    fun delete(now: Instant) {
        if (profile.applicantStatus in setOf(
                ApplicantStatus.SUBMITTED,
                ApplicantStatus.REVIEWING,
                ApplicantStatus.COMPLETED,
            )
        ) {
            throw IdentityDomainException(ErrorCode.ACCOUNT_DELETE_NOT_ALLOWED)
        }
        status = AccountStatus.DELETED
        updatedAt = now
    }

    companion object {
        fun create(
            userId: Long,
            loginId: String,
            passwordHash: PasswordHash,
            role: Role,
            status: AccountStatus,
            profile: StudentProfile,
            createdAt: Instant,
            updatedAt: Instant,
        ): Account = Account(
            userId = userId,
            loginId = loginId,
            passwordHash = passwordHash,
            role = role,
            status = status,
            profile = profile,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )
    }
}
