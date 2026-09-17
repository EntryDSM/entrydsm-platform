package hs.kr.entrydsm.observability.adapterin.web.exception

import hs.kr.entrydsm.observability.domain.enum.ErrorCode
import hs.kr.entrydsm.observability.domain.exception.MonitorDomainException
import jakarta.validation.ConstraintViolationException
import org.junit.Assert.assertEquals
import org.junit.Test
import org.slf4j.MDC
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.validation.BindException
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

class GlobalExceptionHandlerTest {
    private val handler = GlobalExceptionHandler()

    @Test
    fun mapsMonitorExceptionToItsErrorResponse() {
        val response = handler.handleMonitorException(MonitorDomainException(ErrorCode.SESSION_NOT_FOUND))

        assertEquals(404, response.statusCode.value())
        assertEquals("SESSION_NOT_FOUND", response.body?.error?.code)
        assertEquals(ErrorCode.SESSION_NOT_FOUND.message, response.body?.error?.message)
    }

    @Test
    fun mapsInvalidRequestToBadRequestResponse() {
        val response = handler.handleInvalidRequest(IllegalArgumentException())

        assertEquals(400, response.statusCode.value())
        assertEquals("INVALID_PAYLOAD", response.body?.error?.code)
    }

    @Test
    fun mapsValidationExceptionsToBadRequestResponse() {
        val responses = listOf(
            handler.handleInvalidRequest(BindException(this, "request")),
            handler.handleInvalidRequest(ConstraintViolationException(emptySet())),
        )

        responses.forEach { response ->
            assertEquals(400, response.statusCode.value())
            assertEquals("INVALID_PAYLOAD", response.body?.error?.code)
        }
    }

    @Test
    fun answersEventStreamRequestWithErrorStatusInsteadOf500() {
        val mvc = MockMvcBuilders.standaloneSetup(RejectingStreamController())
            .setControllerAdvice(handler)
            .build()

        // 브라우저 EventSource 는 Accept: text/event-stream 만 보낸다.
        mvc.perform(get("/stream").accept(MediaType.TEXT_EVENT_STREAM))
            .andExpect(status().isTooManyRequests)
            .andExpect(jsonPath("$.error.code").value("TOO_MANY_CONNECTIONS"))
    }

    @RestController
    class RejectingStreamController {
        @GetMapping("/stream", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
        fun stream(): SseEmitter = throw MonitorDomainException(ErrorCode.TOO_MANY_CONNECTIONS)
    }

    @Test
    fun keepsGenericResponseAndLogsCorrelationContextForUnhandledException() {
        MDC.put("X-trace-Id", "test-trace-id")
        try {
            val response = handler.handleUnhandledException(IllegalStateException("internal detail"))

            assertEquals(500, response.statusCode.value())
            assertEquals("INTERNAL_SERVER_ERROR", response.body?.error?.code)
            assertEquals(ErrorCode.INTERNAL_SERVER_ERROR.message, response.body?.error?.message)
        } finally {
            MDC.remove("X-trace-Id")
        }
    }
}
