package hs.kr.entrydsm.gateway.adapterin.filter

import java.lang.reflect.Proxy
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.cloud.gateway.filter.GatewayFilterChain
import org.springframework.data.redis.core.HashOperations
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ValueOperations
import org.springframework.data.redis.core.ZSetOperations
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import reactor.core.publisher.Mono
import tools.jackson.databind.json.JsonMapper

class ObservabilityGlobalFilterTest {
    private val mapper = JsonMapper.builder().build()

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
        ObservabilityGlobalFilter(redis, mapper).filter(exchange, GatewayFilterChain { Mono.empty() }).subscribe().dispose()
        slowRedis.countDown()

        assertTrue(recorded.await(5, TimeUnit.SECONDS))
    }

    @Test
    fun recordsCancelledRequestsOnlyAfterTheResponseStarted() {
        val recorded = LinkedBlockingQueue<String>()
        val filter = ObservabilityGlobalFilter(FakeRedis { recorded.add(it) }, mapper)
        fun cancel(chain: GatewayFilterChain) {
            val exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/schedule/v11/time"))
            exchange.response.statusCode = HttpStatus.OK
            filter.filter(exchange, chain).subscribe().dispose()
        }

        cancel { Mono.never() } // 응답 헤더를 보내기 전에 끊김
        cancel { it.response.setComplete().then(Mono.never()) } // 응답을 보낸 뒤 끊김

        assertTrue(requireNotNull(recorded.poll(5, TimeUnit.SECONDS)).startsWith("monitor:metric:api:success:"))
        assertNull(recorded.poll(300, TimeUnit.MILLISECONDS))
    }

    @Test
    fun countsErrorsBeforeTheStatusIsSetAsFailures() {
        val recorded = LinkedBlockingQueue<String>()
        val exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/schedule/v11/time"))
        exchange.response.statusCode = HttpStatus.OK // Reactor Netty 는 상태를 정하기 전에도 200 을 돌려준다.

        ObservabilityGlobalFilter(FakeRedis { recorded.add(it) }, mapper)
            .filter(exchange, GatewayFilterChain { Mono.error(IllegalStateException("request too large")) })
            .onErrorComplete()
            .block()

        assertTrue(requireNotNull(recorded.poll(5, TimeUnit.SECONDS)).startsWith("monitor:metric:api:failure:"))
    }

    @Test
    fun publishesServerErrorsToTheLiveLog() {
        val redis = FakeRedis()
        val exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/document/v11/applications"))
        exchange.response.statusCode = HttpStatus.SERVICE_UNAVAILABLE

        ObservabilityGlobalFilter(redis, mapper).filter(exchange, GatewayFilterChain { Mono.empty() }).block()

        val event = mapper.readTree(requireNotNull(redis.published.poll(5, TimeUnit.SECONDS)))
        assertEquals("SERVER", event["kind"].asString())
        assertEquals("DOCUMENT", event["service"].asString())
        assertEquals(503, event["status"].asInt())
    }

    @Test
    fun keepsClientErrorsOutOfTheLiveLog() {
        val at = Instant.parse("2026-09-27T13:00:00Z")

        assertNull(liveServerError("APPLICATION", "GET", "/api/application/v11/applicants/landing", 401, "HTTP_401", "401 UNAUTHORIZED", 1, at))
        assertEquals(
            "2026-09-27T22:00:00+09:00",
            liveServerError("APPLICATION", "PATCH", "/api/application/v11/applicants", 500, "HTTP_500", "500 INTERNAL_SERVER_ERROR", 1, at)?.get("occurredAt"),
        )
    }

    private class FakeRedis(private val onIncrement: (String) -> Unit = {}) : StringRedisTemplate() {
        val published = LinkedBlockingQueue<String>()

        override fun opsForValue(): ValueOperations<String, String> = fake { method, args ->
            if (method == "increment") onIncrement(args[0] as String)
            1L
        }

        override fun <HK, HV> opsForHash(): HashOperations<String, HK, HV> = fake { _, _ -> 1L }

        override fun opsForZSet(): ZSetOperations<String, String> = fake { _, _ -> null }

        override fun expire(key: String, timeout: Long, unit: TimeUnit): Boolean = true

        override fun convertAndSend(channel: String, message: Any): Long {
            published.add(message as String)
            return 1L
        }

        @Suppress("UNCHECKED_CAST")
        private inline fun <reified T> fake(crossinline answer: (String, Array<Any?>) -> Any?): T =
            Proxy.newProxyInstance(javaClass.classLoader, arrayOf(T::class.java)) { _, method, args ->
                answer(method.name, args ?: emptyArray())
            } as T
    }
}
