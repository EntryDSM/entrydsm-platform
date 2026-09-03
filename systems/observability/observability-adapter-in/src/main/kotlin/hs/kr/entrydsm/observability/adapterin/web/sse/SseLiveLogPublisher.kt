package hs.kr.entrydsm.observability.adapterin.web.sse

import hs.kr.entrydsm.observability.adapterin.web.dto.response.LiveLogEventResponse
import hs.kr.entrydsm.observability.application.port.out.ClientLogInput
import hs.kr.entrydsm.observability.application.port.out.LiveLogPublisherPort
import org.springframework.stereotype.Component

@Component
class SseLiveLogPublisher(
    private val sseBroadcaster: SseBroadcaster,
) : LiveLogPublisherPort {
    override fun publishClientLog(input: ClientLogInput) {
        sseBroadcaster.publishLog(
            LiveLogEventResponse(
                kind = "CLIENT",
                level = input.level.name,
                source = input.source.name,
                message = input.message,
                pageUrl = input.pageUrl,
                occurredAt = input.occurredAt,
            ),
        )
    }
}
