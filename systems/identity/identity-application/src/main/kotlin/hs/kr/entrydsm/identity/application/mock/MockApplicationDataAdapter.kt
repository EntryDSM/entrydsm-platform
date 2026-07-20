package hs.kr.entrydsm.identity.application.mock

import hs.kr.entrydsm.identity.application.port.out.ApplicationDataPort
import hs.kr.entrydsm.identity.application.port.out.data.ApplicationSnapshot
import hs.kr.entrydsm.identity.domain.ApplicantStatus
import hs.kr.entrydsm.identity.domain.ErrorCode
import hs.kr.entrydsm.identity.domain.PassStatus
import hs.kr.entrydsm.identity.domain.exception.IdentityDomainException
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import org.springframework.stereotype.Component

@Component
class MockApplicationDataAdapter : ApplicationDataPort {
    private val applications = ConcurrentHashMap<Long, ApplicationSnapshot>()

    init {
        val now = Instant.parse("2026-06-11T10:00:00Z")
        applications[123L] = ApplicationSnapshot(
            userId = 123L,
            applicantStatus = ApplicantStatus.SUBMITTED,
            submittedAt = now,
            updatedAt = Instant.parse("2026-06-11T10:30:00Z"),
            passStatus = PassStatus.PASSED,
            announcedAt = now,
        )
    }

    override fun create(userId: Long, updatedAt: Instant): ApplicationSnapshot =
        ApplicationSnapshot(
            userId = userId,
            applicantStatus = ApplicantStatus.NONE,
            submittedAt = null,
            updatedAt = updatedAt,
            passStatus = PassStatus.NOT_ANNOUNCED,
            announcedAt = null,
        ).also { applications[userId] = it }

    override fun findByUserId(userId: Long): ApplicationSnapshot? = applications[userId]

    override fun cancel(
        userId: Long,
        reason: String?,
        updatedAt: Instant,
    ): ApplicationSnapshot {
        val current = applications[userId]
            ?: throw IdentityDomainException(ErrorCode.USER_NOT_FOUND)
        if (current.applicantStatus != ApplicantStatus.SUBMITTED) {
            throw IdentityDomainException(ErrorCode.APPLICATION_CANCEL_NOT_ALLOWED)
        }
        return current.copy(
            applicantStatus = ApplicantStatus.CANCELED,
            updatedAt = updatedAt,
        ).also { applications[userId] = it }
    }
}
