package hs.kr.entrydsm.notification.adapterin

import hs.kr.entrydsm.notification.adapterin.web.exception.GlobalExceptionHandler
import hs.kr.entrydsm.notification.application.exception.NotificationNotFoundException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.springframework.http.HttpStatus

class NotificationAdapterInModuleTest {
    @Test
    fun moduleLoads() {
        assertTrue(true)
    }

    @Test
    fun notFoundExceptionReturnsStableErrorResponse() {
        val response = GlobalExceptionHandler()
            .handleNotFound(NotificationNotFoundException("notice not found: id=1"))

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(404, response.body?.status)
        assertEquals("NOTIFICATION_NOT_FOUND", response.body?.code)
        assertEquals("notification not found", response.body?.message)
    }

    @Test
    fun invalidRequestReturnsStableErrorResponse() {
        val response = GlobalExceptionHandler()
            .handleInvalidRequest(IllegalArgumentException("page must be greater than or equal to 0"))

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals(400, response.body?.status)
        assertEquals("INVALID_REQUEST", response.body?.code)
        assertEquals("invalid request", response.body?.message)
    }

    @Test
    fun unhandledExceptionReturnsStableErrorResponse() {
        val response = GlobalExceptionHandler()
            .handleUnhandledException(RuntimeException("database connection failed"))

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.statusCode)
        assertEquals(500, response.body?.status)
        assertEquals("INTERNAL_SERVER_ERROR", response.body?.code)
        assertEquals("internal server error", response.body?.message)
    }
}
