package hs.kr.entrydsm.observability.adapterin.web

import hs.kr.entrydsm.observability.domain.service.KoreaTime
import java.time.Instant
import org.springframework.boot.jackson.JacksonComponent
import tools.jackson.core.JsonGenerator
import tools.jackson.databind.SerializationContext
import tools.jackson.databind.ValueSerializer

/**
 * 응답·SSE 의 시각을 `Z`(UTC) 대신 `+09:00` 으로 내보낸다.
 * 모니터링 화면은 시간대별 그래프 라벨을 시각 문자열에서 그대로 잘라 쓴다(`t.slice(11, 16)`).
 */
@JacksonComponent
class KoreaTimeInstantSerializer : ValueSerializer<Instant>() {
    override fun serialize(value: Instant, gen: JsonGenerator, ctxt: SerializationContext) {
        gen.writeString(KoreaTime.format(value))
    }
}
