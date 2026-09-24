package hs.kr.entrydsm.observability.adapterin.web

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import tools.jackson.databind.json.JsonMapper

class KoreaTimeInstantSerializerTest {
    /** 시간대별 그래프 라벨은 시각 문자열의 시:분을 그대로 쓴다. UTC 로 나가면 한국 자정이 15:00 으로 보인다. */
    @Test
    fun springJsonMapperWritesInstantsInKoreaTime() {
        ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration::class.java))
            .withBean(KoreaTimeInstantSerializer::class.java)
            .run { context ->
                val json = context.getBean(JsonMapper::class.java)
                    .writeValueAsString(Instant.parse("2026-09-21T15:00:00Z"))

                assertEquals("\"2026-09-22T00:00:00+09:00\"", json)
            }
    }
}
