package hs.kr.entrydsm.identity.application.mock

import hs.kr.entrydsm.identity.application.port.out.ApplicationDataPort
import hs.kr.entrydsm.identity.application.port.out.data.ApplicationSnapshot
import hs.kr.entrydsm.identity.domain.enum.ApplicantStatus
import hs.kr.entrydsm.identity.domain.enum.PassStatus
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/** Fallback application-data port required by authentication registration transactions. */
class MockAuthApplicationDataAdapter : ApplicationDataPort {
    private val applicationsByUserId = ConcurrentHashMap<Long, ApplicationSnapshot>()

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
        val current = applicationsByUserId[userId] ?: create(userId, updatedAt)
        return current.copy(
            applicantStatus = ApplicantStatus.CANCELED,
            updatedAt = updatedAt,
        ).also { applicationsByUserId[userId] = it }
    }
}
