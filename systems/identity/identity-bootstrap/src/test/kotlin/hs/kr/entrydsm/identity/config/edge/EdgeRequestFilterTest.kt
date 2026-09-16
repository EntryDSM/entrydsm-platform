package hs.kr.entrydsm.identity.config.edge

import hs.kr.entrydsm.identity.config.edge.EdgeAccessFilterTest.RecordingFilterChain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.slf4j.MDC
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse

class TraceIdFilterTest {
    @Test
    fun generatesTraceIdWhenTheRequestHasNone() {
        val response = MockHttpServletResponse()
        val chain = RecordingFilterChain()

        TraceIdFilter { "generated-trace-id" }.doFilter(MockHttpServletRequest("GET", "/api/notification"), response, chain)

        assertEquals("generated-trace-id", response.getHeader(TraceId.HEADER_NAME))
        assertTrue(chain.invoked)
    }

    @Test
    fun keepsAValidIncomingTraceIdAndExposesItToLogs() {
        val request = MockHttpServletRequest("GET", "/api/notification").apply {
            addHeader(TraceId.HEADER_NAME, "trace-01")
        }
        val response = MockHttpServletResponse()
        var mdcDuringRequest: String? = null

        TraceIdFilter().doFilter(request, response) { _, _ -> mdcDuringRequest = MDC.get(TraceId.HEADER_NAME) }

        assertEquals("trace-01", response.getHeader(TraceId.HEADER_NAME))
        assertEquals("trace-01", mdcDuringRequest)
        assertEquals("trace-01", request.getAttribute(TraceId.HEADER_NAME))
        assertNotNull(MDC.get(TraceId.HEADER_NAME)?.let { "leaked" } ?: "cleared")
    }

    /** 잘못된 값은 되돌려주지 않는다. 그대로 실어 주면 로그 주입에 쓰일 수 있다. */
    @Test
    fun rejectsMalformedTraceIdWithoutEchoingIt() {
        val request = MockHttpServletRequest("GET", "/api/notification").apply {
            addHeader(TraceId.HEADER_NAME, "trace id")
        }
        val response = MockHttpServletResponse()
        val chain = RecordingFilterChain()

        TraceIdFilter().doFilter(request, response, chain)

        assertEquals(400, response.status)
        assertTrue(response.contentAsString.contains("INVALID_TRACE_ID"))
        assertFalse(response.contentAsString.contains("trace id"))
        assertFalse(chain.invoked)
    }
}

class RequestBodyLimitFilterTest {
    @Test
    fun rejectsRequestsThatDeclareTooLargeABody() {
        val request = MockHttpServletRequest("POST", "/api/document/v11/photo").apply {
            setContent(ByteArray(11))
        }
        val response = MockHttpServletResponse()
        val chain = RecordingFilterChain()

        RequestBodyLimitFilter(maxBodyBytes = 10).doFilter(request, response, chain)

        assertEquals(413, response.status)
        assertTrue(response.contentAsString.contains("REQUEST_TOO_LARGE"))
        assertFalse(chain.invoked)
    }

    @Test
    fun allowsABodyExactlyAtTheLimit() {
        val request = MockHttpServletRequest("POST", "/api/document/v11/photo").apply {
            setContent(ByteArray(10))
        }
        val response = MockHttpServletResponse()
        val chain = RecordingFilterChain()

        RequestBodyLimitFilter(maxBodyBytes = 10).doFilter(request, response, chain)

        assertEquals(200, response.status)
        assertTrue(chain.invoked)
    }

    /** 길이를 알려주지 않는 요청은 읽은 바이트로 센다. */
    @Test
    fun rejectsStreamedBodiesThatGrowPastTheLimit() {
        val request = object : MockHttpServletRequest("POST", "/api/document/v11/photo") {
            override fun getContentLengthLong(): Long = -1
        }.apply { setContent(ByteArray(11)) }
        val response = MockHttpServletResponse()

        RequestBodyLimitFilter(maxBodyBytes = 10).doFilter(request, response) { forwarded, _ ->
            forwarded.inputStream.readAllBytes()
        }

        assertEquals(413, response.status)
        assertTrue(response.contentAsString.contains("REQUEST_TOO_LARGE"))
    }
}

class EdgeContractTest {
    @Test
    fun routesOnlyThePathsTheGatewayUsedToRoute() {
        assertTrue(EdgeContract.isRouted("/api/identity/v11/auth/login"))
        assertTrue(EdgeContract.isRouted("/api/v11/admin/applicants"))
        assertTrue(EdgeContract.isRouted("/api/monitor/v11/stream"))
        assertTrue(EdgeContract.isRouted("/actuator/health/readiness"))
        assertFalse(EdgeContract.isRouted("/api/unknown"))
        assertFalse(EdgeContract.isRouted("/"))
        // 접두사만 같은 경로를 통과시키면 안 된다.
        assertFalse(EdgeContract.isRouted("/api/identityx/v11"))
    }

    @Test
    fun treatsOnlyIdentityPathsAsSelfAuthenticating() {
        assertTrue(EdgeContract.isIdentityPath("/api/identity/v11/accounts/me"))
        assertFalse(EdgeContract.isIdentityPath("/api/identityx/v11"))
        assertFalse(EdgeContract.isIdentityPath("/api/application/v11/applicants"))
    }
}
