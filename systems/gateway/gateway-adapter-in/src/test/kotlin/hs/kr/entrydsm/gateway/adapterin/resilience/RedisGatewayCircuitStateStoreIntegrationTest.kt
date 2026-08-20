package hs.kr.entrydsm.gateway.adapterin.resilience

import hs.kr.entrydsm.gateway.adapterin.configuration.GatewayRuntimeProperties
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.data.redis.connection.RedisStandaloneConfiguration
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import java.time.Duration
import java.util.UUID

class RedisGatewayCircuitStateStoreIntegrationTest {
    private var factory: LettuceConnectionFactory? = null
    private var template: ReactiveStringRedisTemplate? = null
    private val routes = mutableSetOf<String>()

    @Test
    fun sharesOpenStateWithNewGatewayInstance() {
        val redisTemplate = connect()
        val policy = policy(waitDurationSeconds = 30)
        val route = route()

        RedisGatewayCircuitStateStore(redisTemplate)
            .record(route, failed = true, halfOpen = false, policy)
            .block(Duration.ofSeconds(2))

        val restartedInstance = RedisGatewayCircuitStateStore(redisTemplate)
        val permit = restartedInstance.tryAcquire(route, policy).block(Duration.ofSeconds(2))

        assertFalse(permit!!.allowed)
    }

    @Test
    fun sharesHalfOpenLeaseAcrossInstances() {
        val redisTemplate = connect()
        val policy = policy(waitDurationSeconds = 1)
        val route = route()
        val first = RedisGatewayCircuitStateStore(redisTemplate)
        val second = RedisGatewayCircuitStateStore(redisTemplate)

        first.record(route, failed = true, halfOpen = false, policy).block(Duration.ofSeconds(2))
        Thread.sleep(1_100)

        val firstPermit = first.tryAcquire(route, policy).block(Duration.ofSeconds(2))!!
        val secondPermit = second.tryAcquire(route, policy).block(Duration.ofSeconds(2))!!

        assertTrue(firstPermit.allowed)
        assertTrue(firstPermit.halfOpen)
        assertFalse(secondPermit.allowed)

        first.releaseHalfOpen(route, firstPermit.permitId).block(Duration.ofSeconds(2))
        assertTrue(second.tryAcquire(route, policy).block(Duration.ofSeconds(2))!!.allowed)
    }

    @Test
    fun allowsNextHalfOpenProbeAfterFailureWhenWaitIsShorterThanProbeTtl() {
        val redisTemplate = connect()
        val policy = policy(waitDurationSeconds = 1)
        val route = route()
        val store = RedisGatewayCircuitStateStore(redisTemplate)

        store.record(route, failed = true, halfOpen = false, policy).block(Duration.ofSeconds(2))
        Thread.sleep(1_100)

        val firstPermit = store.tryAcquire(route, policy).block(Duration.ofSeconds(2))!!
        assertTrue(firstPermit.halfOpen)

        store.record(
            route,
            failed = true,
            halfOpen = true,
            policy = policy,
            permitId = firstPermit.permitId,
        ).block(Duration.ofSeconds(2))
        Thread.sleep(1_100)

        val nextPermit = store.tryAcquire(route, policy).block(Duration.ofSeconds(2))!!

        assertTrue(nextPermit.allowed)
        assertTrue(nextPermit.halfOpen)
    }

    @Test
    fun failsOpenWhenRedisIsUnavailable() {
        val unavailableFactory = LettuceConnectionFactory(RedisStandaloneConfiguration("127.0.0.1", 1))
        unavailableFactory.afterPropertiesSet()
        val unavailableTemplate = ReactiveStringRedisTemplate(unavailableFactory)
        val store = RedisGatewayCircuitStateStore(unavailableTemplate)

        try {
            val permit = store.tryAcquire("redis-outage-${UUID.randomUUID()}", policy())
                .block(Duration.ofSeconds(2))

            assertTrue(permit!!.allowed)
            assertDoesNotThrow {
                store.record(
                    "redis-outage-${UUID.randomUUID()}",
                    failed = true,
                    halfOpen = false,
                    policy(),
                ).block(Duration.ofSeconds(2))
            }
        } finally {
            unavailableFactory.destroy()
        }
    }

    @AfterEach
    fun cleanUp() {
        routes.forEach { route ->
            template?.keys("gateway:circuit:$route:*")
                ?.flatMap { key -> template!!.delete(key) }
                ?.then()
                ?.block(Duration.ofSeconds(2))
        }
        routes.clear()
        factory?.destroy()
        factory = null
        template = null
    }

    private fun connect(): ReactiveStringRedisTemplate {
        val host = System.getProperty("gateway.test.redis.host")
            ?: System.getenv("GATEWAY_TEST_REDIS_HOST")
            ?: "localhost"
        val port = (System.getProperty("gateway.test.redis.port")
            ?: System.getenv("GATEWAY_TEST_REDIS_PORT")
            ?: "6379").toInt()
        val redisFactory = LettuceConnectionFactory(RedisStandaloneConfiguration(host, port))
        redisFactory.afterPropertiesSet()
        val redisTemplate = ReactiveStringRedisTemplate(redisFactory)
        try {
            redisTemplate.opsForValue().get("gateway:circuit:availability-check")
                .block(Duration.ofSeconds(2))
        } catch (error: Exception) {
            redisFactory.destroy()
            throw AssertionError(
                "Redis is required. Configure GATEWAY_TEST_REDIS_HOST/GATEWAY_TEST_REDIS_PORT or gateway.test.redis.*",
                error,
            )
        }
        factory = redisFactory
        template = redisTemplate
        return redisTemplate
    }

    private fun route(): String = "restart-scale-out-${UUID.randomUUID()}".also(routes::add)

    private fun policy(waitDurationSeconds: Long = 30) = GatewayRuntimeProperties.Resilience(
        failureRateThreshold = 100.0,
        slidingWindowSize = 1,
        minimumNumberOfCalls = 1,
        waitDurationSeconds = waitDurationSeconds,
        permittedNumberOfCallsInHalfOpenState = 1,
        stateStore = "redis",
    )
}
