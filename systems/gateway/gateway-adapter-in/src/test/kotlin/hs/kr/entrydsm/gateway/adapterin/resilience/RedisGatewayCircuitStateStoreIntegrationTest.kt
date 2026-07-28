package hs.kr.entrydsm.gateway.adapterin.resilience

import hs.kr.entrydsm.gateway.adapterin.configuration.GatewayRuntimeProperties

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.springframework.data.redis.connection.RedisStandaloneConfiguration
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import java.time.Duration
import java.util.UUID

class RedisGatewayCircuitStateStoreIntegrationTest {
    private var factory: LettuceConnectionFactory? = null
    private var template: ReactiveStringRedisTemplate? = null
    private var routeId: String? = null

    @Test
    fun sharesOpenStateWithNewGatewayInstance() {
        val redisTemplate = connectIfAvailable() ?: return
        val policy = GatewayRuntimeProperties.Resilience(
            failureRateThreshold = 100.0,
            slidingWindowSize = 1,
            minimumNumberOfCalls = 1,
            waitDurationSeconds = 30,
            permittedNumberOfCallsInHalfOpenState = 1,
            stateStore = "redis",
        )
        val route = "restart-scale-out-${UUID.randomUUID()}"
        routeId = route

        RedisGatewayCircuitStateStore(redisTemplate)
            .record(route, failed = true, halfOpen = false, policy)
            .block(Duration.ofSeconds(2))

        val restartedInstance = RedisGatewayCircuitStateStore(redisTemplate)
        val permit = restartedInstance.tryAcquire(route, policy).block(Duration.ofSeconds(2))

        assertFalse(permit!!.allowed)
    }

    @AfterEach
    fun cleanUp() {
        routeId?.let { route ->
            template?.keys("gateway:circuit:$route:*")
                ?.flatMap { key -> template!!.delete(key) }
                ?.then()
                ?.block(Duration.ofSeconds(2))
        }
        factory?.destroy()
    }

    private fun connectIfAvailable(): ReactiveStringRedisTemplate? {
        val redisFactory = LettuceConnectionFactory(RedisStandaloneConfiguration("localhost", 6379))
        redisFactory.afterPropertiesSet()
        val redisTemplate = ReactiveStringRedisTemplate(redisFactory)
        return try {
            redisTemplate.opsForValue().get("gateway:circuit:availability-check")
                .block(Duration.ofSeconds(2))
            factory = redisFactory
            template = redisTemplate
            redisTemplate
        } catch (_: Exception) {
            redisFactory.destroy()
            assumeTrue(false, "Redis is required for the shared circuit state contract test")
            null
        }
    }
}
