package hs.kr.entrydsm.observability.adapterin.web

import java.time.Instant
import org.junit.Assert.assertTrue
import org.junit.Test
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.http.converter.autoconfigure.HttpMessageConvertersAutoConfiguration
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration
import org.springframework.boot.test.context.runner.WebApplicationContextRunner
import org.springframework.boot.webmvc.autoconfigure.DispatcherServletAutoConfiguration
import org.springframework.boot.webmvc.autoconfigure.WebMvcAutoConfiguration
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

class KoreaTimeInstantSerializerTest {
    /**
     * 시간대별 그래프 라벨은 시각 문자열의 시:분을 그대로 쓴다. UTC 로 나가면 한국 자정이 15:00 으로 보인다.
     * 운영과 같은 Spring MVC 메시지 변환기로 SSE 를 보내 화면이 받는 문자열을 확인한다.
     */
    @Test
    fun sseEventWritesInstantsInKoreaTime() {
        WebApplicationContextRunner()
            .withConfiguration(
                AutoConfigurations.of(
                    JacksonAutoConfiguration::class.java,
                    HttpMessageConvertersAutoConfiguration::class.java,
                    DispatcherServletAutoConfiguration::class.java,
                    WebMvcAutoConfiguration::class.java,
                ),
            )
            .withBean(KoreaTimeInstantSerializer::class.java)
            .withBean(InstantStreamController::class.java)
            .run { context ->
                val body = MockMvcBuilders.webAppContextSetup(context).build()
                    .perform(get("/stream").accept(MediaType.TEXT_EVENT_STREAM))
                    .andReturn().response.contentAsString

                assertTrue(body, body.contains("""data:{"at":"2026-09-22T00:00:00+09:00"}"""))
            }
    }

    data class InstantEvent(val at: Instant)

    @RestController
    class InstantStreamController {
        @GetMapping("/stream", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
        fun stream(): SseEmitter = SseEmitter().apply {
            send(SseEmitter.event().data(InstantEvent(Instant.parse("2026-09-21T15:00:00Z"))))
            complete()
        }
    }
}
