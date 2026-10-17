package hs.kr.entrydsm.application.administration.adapterout.distance

import hs.kr.entrydsm.admin.domain.exception.AdminDomainException
import org.junit.Assert.*
import org.junit.Test
import tools.jackson.databind.ObjectMapper
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.sun.net.httpserver.HttpServer
import hs.kr.entrydsm.admin.domain.enum.ErrorCode
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger
import org.slf4j.LoggerFactory

class KakaoDistanceAdapterTest {
    @Test
    fun kakaoDistanceReadsCoordinatesAndMeterValue() {
        val adapter = KakaoDistanceAdapter(ObjectMapper(), "key", "https://local.test", "https://directions.test", "학교", 1000)

        val distance = adapter.parseDistance(
            """{"routes":[{"result_code":0,"summary":{"distance":1234}}]}""",
        )
        val coordinates = adapter.parseCoordinates("""{"documents":[{"x":"127.1","y":"36.3"}]}""")

        assertEquals(1234L, distance)
        assertEquals(KakaoDistanceAdapter.Coordinates(127.1, 36.3), coordinates)
    }

    @Test
    fun kakaoFailureDoesNotExposeApiKey() {
        val adapter = KakaoDistanceAdapter(ObjectMapper(), "super-secret", "::", "::", "학교", 1)

        val exception = runCatching { adapter.distanceFromSchool("집") }.exceptionOrNull()

        assertTrue(exception is AdminDomainException)
        assertFalse(exception?.message.orEmpty().contains("super-secret"))
    }

    @Test
    fun successfulCallsReuseLazySchoolCoordinates() = withServer { base, requests ->
        val adapter = adapter(base)
        capture { logs ->
            assertEquals(1234L, adapter.distanceFromSchool(ADDRESS))
            assertEquals(1234L, adapter.distanceFromSchool(ADDRESS))
            assertEquals(5, requests.get())
            assertTrue(logs.list.isEmpty())
        }
    }

    @Test
    fun httpFailuresIdentifyEachStageWithoutSensitiveResponse() {
        for ((index, stage) in STAGES.withIndex()) {
            withServer(failedRequest = index, status = 401, body = PRIVATE_BODY) { base, _ ->
                capture { logs ->
                    assertUnavailable { adapter(base).distanceFromSchool(ADDRESS) }
                    assertDiagnostic(logs, stage, "HTTP_FAILURE", "httpStatus=401")
                }
            }
        }
    }

    @Test
    fun emptySearchIdentifiesApplicantAndSchool() {
        for ((index, stage) in STAGES.take(2).withIndex()) {
            withServer(failedRequest = index, body = """{"documents":[],"private":"$PRIVATE_BODY"}""") { base, _ ->
                capture { logs ->
                    assertUnavailable { adapter(base).distanceFromSchool(ADDRESS) }
                    assertDiagnostic(logs, stage, "EMPTY_SEARCH")
                }
            }
        }
    }

    @Test
    fun routeFailureRecordsOnlyNumericCode() = withServer(
        failedRequest = 2,
        body = """{"routes":[{"result_code":104,"result_msg":"$PRIVATE_BODY"}]}""",
    ) { base, _ ->
        capture { logs ->
            assertUnavailable { adapter(base).distanceFromSchool(ADDRESS) }
            assertDiagnostic(logs, "DIRECTIONS", "ROUTE_RESULT", "resultCode=104")
        }
    }

    @Test
    fun malformedJsonRecordsExceptionClassWithoutBody() {
        for ((index, stage) in STAGES.withIndex()) {
            withServer(failedRequest = index, body = PRIVATE_BODY) { base, _ ->
                capture { logs ->
                    assertUnavailable { adapter(base).distanceFromSchool(ADDRESS) }
                    assertDiagnostic(logs, stage, "JSON_PARSING", "exceptionClass=tools.jackson.")
                }
            }
        }
    }

    @Test
    fun timeoutRecordsStageAndExceptionClass() = withServer(delayMs = 500) { base, _ ->
        capture { logs ->
            assertUnavailable { adapter(base, timeoutMs = 100).distanceFromSchool(ADDRESS) }
            assertDiagnostic(logs, "APPLICANT_GEOCODING", "TIMEOUT", "exceptionClass=java.net.http.Http")
        }
    }

    @Test
    fun invalidCoordinatesAndMissingDistanceHaveSeparateReasons() {
        for ((index, body, reason) in listOf(
            Triple(0, """{"documents":[{"x":"$PRIVATE_BODY","y":"36.3"}]}""", "COORDINATE_FORMAT"),
            Triple(2, """{"routes":[{"result_code":0,"summary":{}}]}""", "DISTANCE_MISSING"),
        )) {
            withServer(failedRequest = index, body = body) { base, _ ->
                capture { logs ->
                    assertUnavailable { adapter(base).distanceFromSchool(ADDRESS) }
                    assertDiagnostic(logs, STAGES[index], reason)
                }
            }
        }
    }

    private fun adapter(base: String, timeoutMs: Long = 3000) =
        KakaoDistanceAdapter(ObjectMapper(), KEY, "$base/geocode", "$base/directions", SCHOOL, timeoutMs)

    private fun assertUnavailable(action: () -> Unit) {
        val failure = runCatching(action).exceptionOrNull()
        assertTrue("Expected AdminDomainException, got $failure", failure is AdminDomainException)
        assertEquals(ErrorCode.DISTANCE_SERVICE_UNAVAILABLE, (failure as AdminDomainException).errorCode)
        assertEquals(503, failure.errorCode.status)
        assertNull(failure.cause)
    }

    private fun capture(action: (ListAppender<ILoggingEvent>) -> Unit) {
        val logger = LoggerFactory.getLogger(KakaoDistanceAdapter::class.java) as Logger
        val appender = ListAppender<ILoggingEvent>()
        appender.start()
        logger.addAppender(appender)
        try {
            action(appender)
        } finally {
            logger.detachAppender(appender)
            appender.stop()
        }
    }

    private fun assertDiagnostic(logs: ListAppender<ILoggingEvent>, stage: String, reason: String, detail: String? = null) {
        assertEquals(1, logs.list.size)
        val event = logs.list.single()
        val message = event.formattedMessage
        assertTrue(message, message.contains("stage=$stage"))
        assertTrue(message, message.contains("reason=$reason"))
        if (detail != null) assertTrue(message, message.contains(detail))
        assertNull(event.throwableProxy)
        for (secret in listOf(ADDRESS, SCHOOL, KEY, PRIVATE_BODY, "127.123456", "36.654321", "http://", "query=", "origin=")) {
            assertFalse("Sensitive value leaked: $secret", message.contains(secret))
        }
    }

    private fun withServer(
        failedRequest: Int = -1,
        status: Int = 200,
        body: String = "",
        delayMs: Long = 0,
        action: (String, AtomicInteger) -> Unit,
    ) {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val requests = AtomicInteger()
        server.createContext("/") { exchange ->
            val index = requests.getAndIncrement()
            try {
                if (delayMs > 0) Thread.sleep(delayMs)
                val payload = if (index == failedRequest) body else if (exchange.requestURI.path == "/directions") {
                    """{"routes":[{"result_code":0,"summary":{"distance":1234}}]}"""
                } else {
                    """{"documents":[{"x":"127.123456","y":"36.654321"}]}"""
                }
                val bytes = payload.toByteArray(Charsets.UTF_8)
                exchange.sendResponseHeaders(if (index == failedRequest) status else 200, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            } finally {
                exchange.close()
            }
        }
        server.start()
        try {
            action("http://127.0.0.1:${server.address.port}", requests)
        } finally {
            server.stop(0)
        }
    }

    companion object {
        private const val ADDRESS = "지원자-비공개주소"
        private const val SCHOOL = "학교-비공개주소"
        private const val KEY = "private-api-key"
        private const val PRIVATE_BODY = "private-response-and-exception-message"
        private val STAGES = listOf("APPLICANT_GEOCODING", "SCHOOL_GEOCODING", "DIRECTIONS")
    }
}
