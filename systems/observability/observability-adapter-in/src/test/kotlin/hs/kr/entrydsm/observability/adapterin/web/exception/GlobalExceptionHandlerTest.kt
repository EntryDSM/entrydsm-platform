package hs.kr.entrydsm.observability.adapterin.web.exception

import hs.kr.entrydsm.observability.domain.enum.ErrorCode
import hs.kr.entrydsm.observability.domain.exception.MonitorDomainException
import jakarta.validation.ConstraintViolationException
import org.junit.Assert.assertEquals
import org.junit.Test
import org.slf4j.MDC
import org.springframework.validation.BindException

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
