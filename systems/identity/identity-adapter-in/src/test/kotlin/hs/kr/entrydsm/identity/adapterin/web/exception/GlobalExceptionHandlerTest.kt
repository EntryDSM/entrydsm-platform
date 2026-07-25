package hs.kr.entrydsm.identity.adapterin.web.exception

import hs.kr.entrydsm.identity.domain.enum.ErrorCode
import hs.kr.entrydsm.identity.domain.exception.IdentityDomainException
import org.junit.Assert.assertEquals
import org.junit.Test
import org.slf4j.MDC

class GlobalExceptionHandlerTest {
    private val handler = GlobalExceptionHandler()

    @Test
    fun mapsIdentityExceptionToItsErrorResponse() {
        val response = handler.handleIdentityException(
            IdentityDomainException(ErrorCode.ACCOUNT_DELETE_NOT_ALLOWED),
        )

        assertEquals(409, response.statusCode.value())
        assertEquals("ACCOUNT_DELETE_NOT_ALLOWED", response.body?.error?.code)
        assertEquals(ErrorCode.ACCOUNT_DELETE_NOT_ALLOWED.message, response.body?.error?.message)
    }

    @Test
    fun mapsInvalidRequestToBadRequestResponse() {
        val response = handler.handleInvalidRequest(IllegalArgumentException())

        assertEquals(400, response.statusCode.value())
        assertEquals("INVALID_REQUEST_BODY", response.body?.error?.code)
    }

    @Test
    fun keepsGenericResponseAndLogsCorrelationContextForUnhandledException() {
        MDC.put("correlationId", "test-correlation-id")
        try {
            val response = handler.handleUnhandledException(IllegalStateException("internal detail"))

            assertEquals(500, response.statusCode.value())
            assertEquals("INTERNAL_SERVER_ERROR", response.body?.error?.code)
            assertEquals(ErrorCode.INTERNAL_SERVER_ERROR.message, response.body?.error?.message)
        } finally {
            MDC.remove("correlationId")
        }
    }
}
