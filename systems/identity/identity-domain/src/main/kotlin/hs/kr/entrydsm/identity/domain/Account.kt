package hs.kr.entrydsm.identity.domain

import hs.kr.entrydsm.identity.domain.exception.IdentityDomainException
import java.time.Instant

class Account(
    val userId: String,
    val loginId: String,
    private var password: String,
    val role: String,
    var status: AccountStatus,
    val profile: StudentProfile,
    val createdAt: Instant,
    var updatedAt: Instant,
) {
    fun matchesPassword(candidate: String): Boolean = password == candidate

    fun changePassword(newPassword: String, now: Instant) {
        if (password == newPassword) {
            throw IdentityDomainException(ErrorCode.PASSWORD_SAME_AS_OLD)
        }
        password = newPassword
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
            userId: String,
            loginId: String,
            password: String,
            role: String,
            status: AccountStatus,
            profile: StudentProfile,
            createdAt: Instant,
            updatedAt: Instant,
        ): Account = Account(
            userId = userId,
            loginId = loginId,
            password = password,
            role = role,
            status = status,
            profile = profile,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )
    }
}
