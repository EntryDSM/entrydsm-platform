package hs.kr.entrydsm.application.adapterout.repository

import hs.kr.entrydsm.application.adapterout.entity.ApplicantStatusOutboxJpaEntity
import hs.kr.entrydsm.application.api.event.ApplicantStatusChangedEvent
import hs.kr.entrydsm.application.api.event.ApplicantStatusChangedListener
import hs.kr.entrydsm.application.domain.enum.ApplicantStatus
import hs.kr.entrydsm.application.domain.enum.PassResultStatus
import java.time.LocalDateTime
import java.time.ZoneOffset
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import hs.kr.entrydsm.application.api.ApplicantStatus as ApiApplicantStatus
import hs.kr.entrydsm.application.api.PassStatus as ApiPassStatus

/**
 * 저장해 둔 원서 상태 변경을 같은 프로세스의 구독 모듈에 전달한다.
 *
 * 이벤트마다 트랜잭션을 따로 연다. 구독자의 반영과 `published_at` 기록이 함께 커밋되므로 전달이 끝난 이벤트는
 * 다시 보내지 않는다. 한 이벤트가 실패해도 남은 이벤트는 계속 전달하고, 실패한 이벤트는 다음 주기에 다시 시도한다.
 */
@Component
class ApplicantStatusOutboxRelay(
    private val repository: ApplicantStatusOutboxJpaRepository,
    private val listeners: ObjectProvider<ApplicantStatusChangedListener>,
    transactionManager: PlatformTransactionManager,
) {
    private val transaction = TransactionTemplate(transactionManager)

    @Scheduled(fixedDelayString = "\${entrydsm.application.events.relay-delay-ms:1000}")
    fun relay() {
        val subscribers = listeners.orderedStream().toList()
        // 구독 모듈이 없으면 전달한 것으로 표시하지 않는다. 구독자가 올라오면 그때 전달한다.
        if (subscribers.isEmpty()) return

        repository.findTop100ByPublishedAtIsNullOrderByCreatedAtAsc().forEach { pending ->
            try {
                transaction.executeWithoutResult { deliver(pending.eventId, subscribers) }
            } catch (exception: RuntimeException) {
                log.warn("applicant status event delivery failed: eventId={}", pending.eventId, exception)
            }
        }
    }

    private fun deliver(eventId: String, subscribers: List<ApplicantStatusChangedListener>) {
        val outbox = repository.findUnpublishedForUpdate(eventId) ?: return
        val event = outbox.toApplicantStatusChangedEvent()
        subscribers.forEach { it.onApplicantStatusChanged(event) }
        outbox.publishedAt = LocalDateTime.now()
        repository.save(outbox)
    }

    private companion object {
        val log = LoggerFactory.getLogger(ApplicantStatusOutboxRelay::class.java)
    }
}

/** 저장된 시각은 UTC 로 간주한다. 원서 상태 API 와 같은 규칙이다. */
fun ApplicantStatusOutboxJpaEntity.toApplicantStatusChangedEvent() = ApplicantStatusChangedEvent(
    eventId = eventId,
    accountId = accountId,
    applicantStatus = when (applicantStatus) {
        ApplicantStatus.DRAFT -> ApiApplicantStatus.DRAFT
        ApplicantStatus.SUBMITTED -> ApiApplicantStatus.SUBMITTED
        ApplicantStatus.REVIEWING -> ApiApplicantStatus.REVIEWING
        ApplicantStatus.COMPLETED -> ApiApplicantStatus.COMPLETED
        ApplicantStatus.CANCELED -> ApiApplicantStatus.CANCELED
    },
    passStatus = when (passStatus) {
        PassResultStatus.PENDING -> ApiPassStatus.NOT_ANNOUNCED
        PassResultStatus.PASS -> ApiPassStatus.PASSED
        PassResultStatus.FAIL -> ApiPassStatus.FAILED
    },
    version = statusVersion,
    submittedAt = submittedAt?.toInstant(ZoneOffset.UTC),
    announcedAt = announcedAt?.toInstant(ZoneOffset.UTC),
    occurredAt = createdAt.toInstant(ZoneOffset.UTC),
)
