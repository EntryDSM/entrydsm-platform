package hs.kr.entrydsm.identity.adapterout.persistence

import hs.kr.entrydsm.identity.adapterout.entity.ApplicationProjectionJpaEntity
import hs.kr.entrydsm.identity.adapterout.entity.IdentityOutboxJpaEntity
import hs.kr.entrydsm.identity.adapterout.repository.ApplicationProjectionJpaRepository
import hs.kr.entrydsm.identity.adapterout.repository.IdentityOutboxJpaRepository
import hs.kr.entrydsm.identity.application.port.out.ApplicationDataPort
import hs.kr.entrydsm.identity.application.port.out.ApplicationEventConsumer
import hs.kr.entrydsm.identity.application.port.out.ApplicationOutboxPort
import hs.kr.entrydsm.identity.application.port.out.data.ApplicationOutboxEvent
import hs.kr.entrydsm.identity.application.port.out.data.ApplicationSnapshot
import hs.kr.entrydsm.identity.application.port.out.data.ApplicationStateChangedEvent
import hs.kr.entrydsm.identity.domain.enum.ApplicantStatus
import hs.kr.entrydsm.identity.domain.enum.ErrorCode
import hs.kr.entrydsm.identity.domain.exception.IdentityDomainException
import java.time.Instant
import java.util.UUID
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/** Owns the local application projection and its transactional outbox. */
@Component
@Primary
@Profile("prod", "dev", "integration")
class AccountApplicationDataPersistenceAdapter(
    private val projectionRepository: ApplicationProjectionJpaRepository,
    private val outboxRepository: IdentityOutboxJpaRepository,
) : ApplicationDataPort, ApplicationEventConsumer, ApplicationOutboxPort {
    @Transactional
    override fun create(userId: Long, updatedAt: Instant): ApplicationSnapshot =
        projectionRepository.findById(userId).orElseGet {
            projectionRepository.save(
                ApplicationProjectionJpaEntity(
                    userId = userId,
                    stateUpdatedAt = updatedAt,
                ),
            )
        }.toSnapshot()

    @Transactional(readOnly = true)
    override fun findByUserId(userId: Long): ApplicationSnapshot? =
        projectionRepository.findById(userId).orElse(null)?.toSnapshot()

    @Transactional
    override fun cancel(
        userId: Long,
        reason: String?,
        updatedAt: Instant,
    ): ApplicationSnapshot {
        val projection = projectionRepository.findByUserIdForUpdate(userId)
            ?: throw IdentityDomainException(ErrorCode.USER_NOT_FOUND)
        if (projection.applicantStatus != ApplicantStatus.SUBMITTED) {
            throw IdentityDomainException(ErrorCode.APPLICATION_CANCEL_NOT_ALLOWED)
        }

        projection.applicantStatus = ApplicantStatus.CANCELED
        projection.stateUpdatedAt = updatedAt
        projection.sourceVersion += 1
        projectionRepository.save(projection)
        outboxRepository.save(
            IdentityOutboxJpaEntity(
                eventId = UUID.randomUUID().toString(),
                userId = userId,
                sourceVersion = projection.sourceVersion,
                applicantStatus = projection.applicantStatus,
                submittedAt = projection.submittedAt,
                passStatus = projection.passStatus,
                announcedAt = projection.announcedAt,
                occurredAt = updatedAt,
                reason = reason,
            ),
        )
        return projection.toSnapshot()
    }

    @Transactional
    override fun consume(event: ApplicationStateChangedEvent): Boolean {
        val projection = projectionRepository.findByUserIdForUpdate(event.userId)
        if (projection != null &&
            (event.version <= projection.sourceVersion || event.eventId == projection.lastEventId)
        ) {
            return false
        }

        val resolved = projection ?: ApplicationProjectionJpaEntity(userId = event.userId)
        resolved.applicantStatus = event.applicantStatus
        resolved.submittedAt = event.submittedAt
        resolved.passStatus = event.passStatus
        resolved.announcedAt = event.announcedAt
        resolved.stateUpdatedAt = event.occurredAt
        resolved.sourceVersion = event.version
        resolved.lastEventId = event.eventId
        projectionRepository.save(resolved)
        return true
    }

    @Transactional(readOnly = true)
    override fun pending(limit: Int): List<ApplicationOutboxEvent> =
        outboxRepository.findTop100ByPublishedAtIsNullOrderByCreatedAtAsc()
            .take(limit.coerceAtLeast(0))
            .map { it.toOutboxEvent() }

    @Transactional
    override fun markPublished(eventId: String, publishedAt: Instant) {
        outboxRepository.findById(eventId).ifPresent {
            it.publishedAt = publishedAt
            outboxRepository.save(it)
        }
    }

    @Transactional
    override fun markFailed(eventId: String, failure: String) {
        outboxRepository.findById(eventId).ifPresent {
            it.attempts += 1
            it.lastError = failure.take(LAST_ERROR_MAX_LENGTH)
            outboxRepository.save(it)
        }
    }

    private fun ApplicationProjectionJpaEntity.toSnapshot(): ApplicationSnapshot = ApplicationSnapshot(
        userId = userId,
        applicantStatus = applicantStatus,
        submittedAt = submittedAt,
        updatedAt = stateUpdatedAt,
        passStatus = passStatus,
        announcedAt = announcedAt,
    )

    private fun IdentityOutboxJpaEntity.toOutboxEvent(): ApplicationOutboxEvent = ApplicationOutboxEvent(
        eventId = eventId,
        userId = userId,
        version = sourceVersion,
        applicantStatus = applicantStatus,
        submittedAt = submittedAt,
        passStatus = passStatus,
        announcedAt = announcedAt,
        occurredAt = occurredAt,
        attempts = attempts,
        lastError = lastError,
    )

    private companion object {
        const val LAST_ERROR_MAX_LENGTH = 1000
    }
}
