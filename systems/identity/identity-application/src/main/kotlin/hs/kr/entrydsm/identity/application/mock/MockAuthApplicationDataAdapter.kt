package hs.kr.entrydsm.identity.application.mock

import hs.kr.entrydsm.identity.application.port.out.ApplicationDataPort
import hs.kr.entrydsm.identity.application.port.out.data.ApplicationSnapshot
import hs.kr.entrydsm.identity.domain.enum.ApplicantStatus
import hs.kr.entrydsm.identity.domain.enum.ErrorCode
import hs.kr.entrydsm.identity.domain.enum.PassStatus
import hs.kr.entrydsm.identity.domain.exception.IdentityDomainException
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/** Fallback application-data port required by authentication registration transactions. */
class MockAuthApplicationDataAdapter(
    initialApplications: Map<Long, ApplicationSnapshot> = emptyMap(),
) : ApplicationDataPort {
    private val applicationsByUserId = ConcurrentHashMap(initialApplications)

    override fun create(userId: Long, updatedAt: Instant): ApplicationSnapshot =
        ApplicationSnapshot(
            userId = userId,
            applicantStatus = ApplicantStatus.NONE,
            submittedAt = null,
            updatedAt = updatedAt,
            passStatus = PassStatus.NOT_ANNOUNCED,
            announcedAt = null,
        ).also { applicationsByUserId[userId] = it }

    override fun findByUserId(userId: Long): ApplicationSnapshot? = applicationsByUserId[userId]

    override fun cancel(userId: Long, reason: String?, updatedAt: Instant): ApplicationSnapshot {
        return requireNotNull(
            applicationsByUserId.compute(userId) { _, current ->
                val application = current
                    ?: throw IdentityDomainException(ErrorCode.USER_NOT_FOUND)
                if (application.applicantStatus != ApplicantStatus.SUBMITTED) {
                    throw IdentityDomainException(ErrorCode.APPLICATION_CANCEL_NOT_ALLOWED)
                }
                application.copy(
                    applicantStatus = ApplicantStatus.CANCELED,
                    updatedAt = updatedAt,
                )
            },
        )
    }
}
