package hs.kr.entrydsm.identity.adapterout.application

import hs.kr.entrydsm.application.api.event.ApplicantStatusChangedEvent
import hs.kr.entrydsm.application.api.event.ApplicantStatusChangedListener
import hs.kr.entrydsm.identity.application.port.out.ApplicationEventConsumer
import hs.kr.entrydsm.identity.application.port.out.data.ApplicationStateChangedEvent
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

/**
 * application 모듈의 원서 상태 변경을 identity 의 원서 투영에 반영한다. Redis Stream 소비자(`ApplicationStatusRedisConsumer`)를 대신한다.
 *
 * 멱등성은 [ApplicationEventConsumer] 가 버전과 이벤트 식별자로 보장한다. 반영에 실패하면 예외를 그대로 던져
 * application 의 전달자가 다음 주기에 다시 보내게 한다.
 */
@Component
@Profile("prod", "dev", "integration")
class ApplicantStatusChangedEventListener(
    private val eventConsumer: ApplicationEventConsumer,
) : ApplicantStatusChangedListener {
    override fun onApplicantStatusChanged(event: ApplicantStatusChangedEvent) {
        eventConsumer.consume(
            ApplicationStateChangedEvent(
                eventId = event.eventId,
                userId = event.accountId,
                version = event.version,
                applicantStatus = event.applicantStatus.toIdentityApplicantStatus(),
                submittedAt = event.submittedAt,
                passStatus = event.passStatus.toIdentityPassStatus(),
                announcedAt = event.announcedAt,
                occurredAt = event.occurredAt,
            ),
        )
    }
}
