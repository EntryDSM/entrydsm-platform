package hs.kr.entrydsm.identity.application.port.out

import hs.kr.entrydsm.identity.application.port.out.data.ApplicationOutboxEvent
import java.time.Instant

interface ApplicationOutboxPort {
    fun pending(limit: Int = 100): List<ApplicationOutboxEvent>

    fun markPublished(eventId: String, publishedAt: Instant)

    fun markFailed(eventId: String, failure: String)
}
