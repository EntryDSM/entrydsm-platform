package hs.kr.entrydsm.application.adapterin.web.exception

import org.junit.Assert.assertEquals
import org.junit.Test
import org.springframework.http.HttpMethod
import org.springframework.web.HttpRequestMethodNotSupportedException
import org.springframework.web.servlet.resource.NoResourceFoundException

class GlobalExceptionHandlerTest {
    private val handler = GlobalExceptionHandler()

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
}
