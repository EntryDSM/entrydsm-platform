package hs.kr.entrydsm.observability.adapterin.web.dto.response

import java.time.Instant

data class LiveLogEventResponse(
    val kind: String,
    val level: String,
    val source: String,
    val message: String,
    val pageUrl: String,
    val occurredAt: Instant,
)
