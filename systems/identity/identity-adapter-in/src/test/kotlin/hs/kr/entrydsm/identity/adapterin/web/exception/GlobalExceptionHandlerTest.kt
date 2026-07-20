package hs.kr.entrydsm.identity.adapterin.web.exception

import hs.kr.entrydsm.identity.domain.enum.ErrorCode
import hs.kr.entrydsm.identity.domain.exception.IdentityException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.springframework.http.HttpStatus

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
    fun handlesInvalidRequestExceptionWithBadRequestErrorCode() {
        val response = handler.handleInvalidRequest(IllegalArgumentException())

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals("INVALID_REQUEST_BODY", response.body?.error?.code)
    }

    @Test
    fun handlesUnexpectedExceptionWithInternalServerErrorCode() {
        val response = handler.handleUnhandledException(IllegalStateException())

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.statusCode)
        assertEquals("INTERNAL_SERVER_ERROR", response.body?.error?.code)
    }

    private class TestIdentityException(errorCode: ErrorCode) : IdentityException(errorCode)
}
