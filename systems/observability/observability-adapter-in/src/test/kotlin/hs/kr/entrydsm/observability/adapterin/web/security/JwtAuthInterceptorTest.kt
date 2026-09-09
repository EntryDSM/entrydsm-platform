package hs.kr.entrydsm.observability.adapterin.web.security

import hs.kr.entrydsm.observability.domain.enum.ErrorCode
import hs.kr.entrydsm.observability.domain.exception.MonitorDomainException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse

class JwtAuthInterceptorTest {
    private val interceptor = JwtAuthInterceptor()

    private fun request(role: String): MockHttpServletRequest = MockHttpServletRequest().apply {
        addHeader("X-User-Id", "1")
        addHeader("X-User-Role", role)
    }

    @Test
    fun allowsMonitorRole() {
        val allowed = interceptor.preHandle(request("MONITOR"), MockHttpServletResponse(), Any())
        assertTrue(allowed)
    }

    private fun rejectionCode(request: MockHttpServletRequest): ErrorCode =
        assertThrows(MonitorDomainException::class.java) {
            interceptor.preHandle(request, MockHttpServletResponse(), Any())
        }.errorCode

    @Test
    fun rejectsMissingIdentityWithUnauthorized() {
        assertEquals(ErrorCode.UNAUTHORIZED, rejectionCode(MockHttpServletRequest()))
    }

    @Test
    fun rejectsNonMonitorRoleWithForbidden() {
        assertEquals(ErrorCode.FORBIDDEN, rejectionCode(request("ADMIN")))
    }
}
