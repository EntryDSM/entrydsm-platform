package hs.kr.entrydsm.application.adapterin.web.exception

import hs.kr.entrydsm.application.application.exception.ApplicationPeriodClosedException
import hs.kr.entrydsm.application.application.exception.ApplicationPeriodLookupFailedException
import org.junit.Assert.assertEquals
import org.junit.Test
import org.springframework.http.HttpMethod
import org.springframework.web.HttpRequestMethodNotSupportedException
import org.springframework.web.servlet.resource.NoResourceFoundException

class GlobalExceptionHandlerTest {
    private val handler = GlobalExceptionHandler()

    @Test
    fun phoneLookupFailureReturns503InsteadOfAllowingPersonalInfoUpdate() {
        val response = handler.handleAccountPhoneLookupFailed(
            hs.kr.entrydsm.application.application.exception.AccountPhoneLookupFailedException(IllegalStateException("private-phone")),
        )
        assertEquals(503, response.statusCode.value())
        assertEquals("ACCOUNT_SERVICE_UNAVAILABLE", response.body?.error?.code)
        org.junit.Assert.assertFalse(response.body?.error?.message.orEmpty().contains("private-phone"))
    }

    @Test
    fun unknownBadRequestsLogContextWithoutPrivateValues() {
        val logger = org.slf4j.LoggerFactory.getLogger(GlobalExceptionHandler::class.java) as ch.qos.logback.classic.Logger
        val appender = ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>()
        appender.start()
        logger.addAppender(appender)
        org.slf4j.MDC.put("correlationId", "test-correlation")
        try {
            val request = org.springframework.mock.web.MockHttpServletRequest("PATCH", "/api/application/v11/applicants/personal").apply {
                queryString = "private-query"
                addHeader("Authorization", "private-token")
                setContent("private-body".toByteArray())
            }
            val response = handler.handleInvalidRequest(IllegalArgumentException("private-message", IllegalStateException("private-cause")), request)
            assertEquals(400, response.statusCode.value())
            assertEquals("INVALID_REQUEST", response.body?.error?.code)
            assertEquals("요청 형식을 확인해주세요.", response.body?.error?.message)
            val event = appender.list.single()
            assertEquals(ch.qos.logback.classic.Level.WARN, event.level)
            org.junit.Assert.assertTrue(event.formattedMessage.contains("test-correlation"))
            org.junit.Assert.assertTrue(event.formattedMessage.contains("method=PATCH"))
            org.junit.Assert.assertTrue(event.formattedMessage.contains("exceptionType=java.lang.IllegalArgumentException"))
            org.junit.Assert.assertTrue(event.formattedMessage.contains("causeType=java.lang.IllegalStateException"))
            org.junit.Assert.assertFalse(event.formattedMessage.contains("private-"))
            org.junit.Assert.assertNull(event.throwableProxy)
        } finally {
            logger.detachAppender(appender)
            appender.stop()
            org.slf4j.MDC.remove("correlationId")
        }
    }

    @Test
    fun mapsUnknownPathToNotFoundResponse() {
        val response = handler.handleApiNotFound(
            NoResourceFoundException(HttpMethod.GET, "/api/application/v1/applicants", "api/application/v1/applicants"),
        )

        assertEquals(404, response.statusCode.value())
        assertEquals("API_NOT_FOUND", response.body?.error?.code)
    }

    @Test
    fun mapsUnsupportedMethodToMethodNotAllowedResponseWithAllowHeader() {
        val response = handler.handleMethodNotAllowed(HttpRequestMethodNotSupportedException("DELETE", listOf("PATCH")))

        assertEquals(405, response.statusCode.value())
        assertEquals("METHOD_NOT_ALLOWED", response.body?.error?.code)
        assertEquals(setOf(HttpMethod.PATCH), response.headers.allow)
    }

    @Test
    fun mapsClosedApplicationPeriodToForbiddenAndLookupFailureToServiceUnavailable() {
        val closed = handler.handleApplicationPeriodClosed(ApplicationPeriodClosedException())
        val unavailable = handler.handleApplicationPeriodLookupFailed(
            ApplicationPeriodLookupFailedException(IllegalStateException("configuration is down")),
        )

        assertEquals(403, closed.statusCode.value())
        assertEquals("APPLICATION_PERIOD_CLOSED", closed.body?.error?.code)
        assertEquals(503, unavailable.statusCode.value())
        assertEquals("SCHEDULE_SERVICE_UNAVAILABLE", unavailable.body?.error?.code)
    }
}
