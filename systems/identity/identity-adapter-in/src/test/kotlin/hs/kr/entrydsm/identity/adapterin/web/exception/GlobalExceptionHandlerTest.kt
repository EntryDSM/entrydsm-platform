package hs.kr.entrydsm.identity.adapterin.web.exception

import hs.kr.entrydsm.identity.domain.enum.ErrorCode
import hs.kr.entrydsm.identity.domain.exception.IdentityDomainException
import hs.kr.entrydsm.identity.domain.exception.IdentityException
import jakarta.validation.ConstraintViolationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.slf4j.MDC
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.validation.BindException
import org.springframework.web.HttpRequestMethodNotSupportedException
import org.springframework.web.servlet.resource.NoResourceFoundException

class GlobalExceptionHandlerTest {
    private val handler = GlobalExceptionHandler()

    @Test
    fun handlesIdentityExceptionUsingItsErrorCode() {
        val response = handler.handleIdentityException(TestIdentityException(ErrorCode.USER_NOT_FOUND))

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(false, response.body?.success)
        assertEquals("USER_NOT_FOUND", response.body?.error?.code)
        assertEquals("사용자를 찾을 수 없습니다.", response.body?.error?.message)
        assertEquals(404, response.body?.error?.status)
        assertNotNull(response.body?.timestamp)
    }

    @Test
    fun mapsDomainExceptionToItsErrorResponse() {
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

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals("INVALID_REQUEST_BODY", response.body?.error?.code)
    }

    @Test
    fun mapsSignupBirthdateErrorsToClientResponses() {
        val cases =
            listOf(
                ErrorCode.INVALID_BIRTHDATE to 400,
                ErrorCode.SIGNUP_AGE_RESTRICTION to 403,
            )
        for ((errorCode, status) in cases) {
            val response = handler.handleIdentityException(IdentityDomainException(errorCode))
            assertEquals(status, response.statusCode.value())
            assertEquals(false, response.body?.success)
            assertEquals(errorCode.name, response.body?.error?.code)
            assertEquals(errorCode.message, response.body?.error?.message)
        }
    }

    @Test
    fun mapsValidationExceptionsToBadRequestResponse() {
        val responses = listOf(
            handler.handleInvalidRequest(BindException(this, "request")),
            handler.handleInvalidRequest(ConstraintViolationException(emptySet())),
        )

        responses.forEach { response ->
            assertEquals(400, response.statusCode.value())
            assertEquals("INVALID_REQUEST_BODY", response.body?.error?.code)
        }
    }

    @Test
    fun mapsUnknownPathToNotFoundResponse() {
        val response = handler.handleApiNotFound(
            NoResourceFoundException(HttpMethod.GET, "/api/identity/v1/auth", "api/identity/v1/auth"),
        )

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals("API_NOT_FOUND", response.body?.error?.code)
    }

    @Test
    fun mapsUnsupportedMethodToMethodNotAllowedResponseWithAllowHeader() {
        val response = handler.handleMethodNotAllowed(HttpRequestMethodNotSupportedException("GET", listOf("POST")))

        assertEquals(HttpStatus.METHOD_NOT_ALLOWED, response.statusCode)
        assertEquals("METHOD_NOT_ALLOWED", response.body?.error?.code)
        assertEquals(setOf(HttpMethod.POST), response.headers.allow)
    }

    @Test
    fun keepsGenericResponseAndLogsTraceContextForUnhandledException() {
        MDC.put("X-trace-Id", "test-trace-id")
        try {
            val response = handler.handleUnhandledException(IllegalStateException("internal detail"))

            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.statusCode)
            assertEquals("INTERNAL_SERVER_ERROR", response.body?.error?.code)
        } finally {
            MDC.remove("X-trace-Id")
        }
    }

    private class TestIdentityException(errorCode: ErrorCode) : IdentityException(errorCode)
}
