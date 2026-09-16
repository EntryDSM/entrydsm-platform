package hs.kr.entrydsm.identity.adapterout.application

import hs.kr.entrydsm.application.api.ApplicantStatus
import hs.kr.entrydsm.application.api.PassStatus
import hs.kr.entrydsm.application.api.event.ApplicantStatusChangedEvent
import hs.kr.entrydsm.identity.adapterout.persistence.AccountApplicationDataPersistenceAdapter
import hs.kr.entrydsm.identity.application.port.out.ApplicationEventConsumer
import hs.kr.entrydsm.identity.application.port.out.data.ApplicationStateChangedEvent
import hs.kr.entrydsm.identity.domain.enum.ApplicantStatus as DomainApplicantStatus
import hs.kr.entrydsm.identity.domain.enum.PassStatus as DomainPassStatus
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.springframework.context.annotation.Profile

class ApplicantStatusChangedEventListenerTest {
    @Test
    fun forwardsStatusChangeToProjectionConsumer() {
        val consumer = RecordingEventConsumer()

        ApplicantStatusChangedEventListener(consumer).onApplicantStatusChanged(event())

        assertEquals(
            ApplicationStateChangedEvent(
                eventId = EVENT_ID,
                userId = 10L,
                version = 3L,
                applicantStatus = DomainApplicantStatus.SUBMITTED,
                submittedAt = SUBMITTED_AT,
                passStatus = DomainPassStatus.FAILED,
                announcedAt = ANNOUNCED_AT,
                occurredAt = OCCURRED_AT,
            ),
            consumer.consumed.single(),
        )
    }

    /** 반영에 실패하면 전달자가 다시 보내야 하므로 예외를 삼키지 않는다. */
    @Test
    fun propagatesProjectionFailureSoTheEventIsRedelivered() {
        val consumer = RecordingEventConsumer(failure = IllegalStateException("Student profile not found for account 10"))

        assertThrows(IllegalStateException::class.java) {
            ApplicantStatusChangedEventListener(consumer).onApplicantStatusChanged(event())
        }
    }

    /** 리스너와 in-process 어댑터는 이들이 의존하는 투영 어댑터와 같은 프로파일에서만 올라가야 한다. */
    @Test
    fun sharesRuntimeProfilesWithTheProjectionAdapter() {
        val projectionProfiles = profileOf(AccountApplicationDataPersistenceAdapter::class.java)

        assertEquals(setOf("prod", "dev", "integration"), projectionProfiles)
        assertEquals(projectionProfiles, profileOf(ApplicantStatusChangedEventListener::class.java))
        assertEquals(projectionProfiles, profileOf(ApplicationApiDataAdapter::class.java))
    }

    private fun profileOf(type: Class<*>): Set<String> =
        requireNotNull(type.getAnnotation(Profile::class.java)).value.toSet()

    private fun event() = ApplicantStatusChangedEvent(
        eventId = EVENT_ID,
        accountId = 10L,
        applicantStatus = ApplicantStatus.SUBMITTED,
        passStatus = PassStatus.FAILED,
        version = 3L,
        submittedAt = SUBMITTED_AT,
        announcedAt = ANNOUNCED_AT,
        occurredAt = OCCURRED_AT,
    )

    private class RecordingEventConsumer(
        private val failure: RuntimeException? = null,
    ) : ApplicationEventConsumer {
        val consumed = mutableListOf<ApplicationStateChangedEvent>()

        override fun consume(event: ApplicationStateChangedEvent): Boolean {
            failure?.let { throw it }
            consumed += event
            return true
        }
    }

    private companion object {
        const val EVENT_ID = "3f2c1e5a-8a55-4a1b-9c62-5d1f0e7a9b10"
        val SUBMITTED_AT: Instant = Instant.parse("2026-09-01T00:00:00Z")
        val ANNOUNCED_AT: Instant = Instant.parse("2026-11-01T00:00:00Z")
        val OCCURRED_AT: Instant = Instant.parse("2026-11-01T00:00:01Z")
    }
}
