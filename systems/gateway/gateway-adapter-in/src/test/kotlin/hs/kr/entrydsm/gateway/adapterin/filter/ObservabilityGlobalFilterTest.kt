package hs.kr.entrydsm.gateway.adapterin.filter

import java.lang.reflect.Proxy
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.cloud.gateway.filter.GatewayFilterChain
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ValueOperations
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import reactor.core.publisher.Mono

class ObservabilityGlobalFilterTest {
    @Test
    fun classifiesBusinessRequests() {
        assertEquals("application-submit", businessMetric(HttpMethod.PATCH, "/api/application/v11/applicants"))
        assertEquals("pdf-download", businessMetric(HttpMethod.GET, "/api/document/v11/applications/12"))
        assertNull(businessMetric(HttpMethod.GET, "/api/application/v11/applicants"))
    }

    @Test
    fun mapsGatewayPathsToObservedServices() {
        assertEquals("APPLICATION", observedService("/api/application/v11/applicants"))
        assertEquals("DOCUMENT", observedService("/api/document/v11/applications"))
        assertNull(observedService("/api/v11/admin/users"))
    }

    @Test
    fun keepsRecordingAfterTheRequestIsCancelledRightAfterTheResponse() {
        val slowRedis = CountDownLatch(1)
        val recorded = CountDownLatch(1)
        val redis = FakeRedis {
            slowRedis.await()
            recorded.countDown()
        }
        val exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/schedule/v11/time"))
        exchange.response.statusCode = HttpStatus.OK

        // 응답 직후 연결이 닫히면 서버가 요청 구독을 취소한다.
        ObservabilityGlobalFilter(redis).filter(exchange, GatewayFilterChain { Mono.empty() }).subscribe().dispose()
        slowRedis.countDown()

        assertTrue(recorded.await(5, TimeUnit.SECONDS))
    }

    private class FakeRedis(private val onIncrement: (String) -> Unit) : StringRedisTemplate() {
        @Suppress("UNCHECKED_CAST")
        override fun opsForValue(): ValueOperations<String, String> =
            Proxy.newProxyInstance(javaClass.classLoader, arrayOf(ValueOperations::class.java)) { _, method, args ->
                check(method.name == "increment") { "unexpected call: ${method.name}" }
                onIncrement(args[0] as String)
                1L
            } as ValueOperations<String, String>

        override fun expire(key: String, timeout: Long, unit: TimeUnit): Boolean = true
    }
}
