package hs.kr.entrydsm.identity.adapterout.security

import hs.kr.entrydsm.identity.application.port.out.RefreshTokenStoreUnavailableException
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.AfterClass
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.springframework.data.redis.connection.RedisStandaloneConfiguration
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.testcontainers.DockerClientFactory
import org.testcontainers.containers.GenericContainer
import org.testcontainers.utility.DockerImageName

class RedisRefreshTokenRotationAdapterIntegrationTest {
    @Before
    fun clearState() {
        template.keys("$KEY_PREFIX*").forEach(template::delete)
    }

    @Test
    fun concurrentConsumeAllowsOnlyOneWinner() {
        val workers = 16
        val ready = CountDownLatch(workers)
        val start = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(workers)

        try {
            val results = (0 until workers).map {
                executor.submit<Boolean> {
                    ready.countDown()
                    assertTrue(start.await(5, TimeUnit.SECONDS))
                    adapter.consume("concurrent-token", Instant.now().plusSeconds(30))
                }
            }
            assertTrue(ready.await(5, TimeUnit.SECONDS))
            start.countDown()

            assertTrue(results.count { it.get(10, TimeUnit.SECONDS) } == 1)
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun reusedTokenIsRejected() {
        val expiresAt = Instant.now().plusSeconds(30)

        assertTrue(adapter.consume("reused-token", expiresAt))
        assertFalse(adapter.consume("reused-token", expiresAt))
    }

    @Test
    fun consumedTokenCanBeUsedAfterTtlExpires() {
        val tokenId = "ttl-token"
        assertTrue(adapter.consume(tokenId, Instant.now().plusMillis(500)))

        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        var acceptedAfterExpiry = false
        while (System.nanoTime() < deadline && !acceptedAfterExpiry) {
            Thread.sleep(100)
            acceptedAfterExpiry = adapter.consume(tokenId, Instant.now().plusSeconds(30))
        }

        assertTrue(acceptedAfterExpiry)
    }

    @Test
    fun redisFailureIsMappedToStoreUnavailable() {
        val unavailableFactory = LettuceConnectionFactory(
            RedisStandaloneConfiguration("127.0.0.1", 1),
        )
        unavailableFactory.afterPropertiesSet()
        val unavailableTemplate = StringRedisTemplate(unavailableFactory)
        unavailableTemplate.afterPropertiesSet()
        val unavailableAdapter = RedisRefreshTokenRotationAdapter(
            redisTemplate = unavailableTemplate,
            clock = Clock.systemUTC(),
            namespace = NAMESPACE,
            issuer = ISSUER,
        )

        try {
            val exception = try {
                unavailableAdapter.consume("unavailable-token", Instant.now().plusSeconds(30))
                null
            } catch (thrown: RefreshTokenStoreUnavailableException) {
                thrown
            }

            assertNotNull(exception)
        } finally {
            unavailableFactory.destroy()
        }
    }

    private companion object {
        const val REDIS_IMAGE = "redis:7.4-alpine"
        const val REDIS_PORT = 6379
        const val NAMESPACE = "integration"
        const val ISSUER = "entrydsm-identity"
        val KEY_PREFIX = "$NAMESPACE:$ISSUER:identity:auth:refresh:"
        lateinit var redis: GenericContainer<Nothing>
        lateinit var connectionFactory: LettuceConnectionFactory
        lateinit var template: StringRedisTemplate
        lateinit var adapter: RedisRefreshTokenRotationAdapter
        var redisStarted = false

        @JvmStatic
        @BeforeClass
        fun startRedis() {
            assumeTrue(
                "Docker daemon is required for Redis integration tests",
                DockerClientFactory.instance().isDockerAvailable,
            )
            redis = GenericContainer<Nothing>(DockerImageName.parse(REDIS_IMAGE))
                .withExposedPorts(REDIS_PORT)
            redis.start()
            redisStarted = true

            connectionFactory = LettuceConnectionFactory(
                RedisStandaloneConfiguration(redis.host, redis.getMappedPort(REDIS_PORT)),
            )
            connectionFactory.afterPropertiesSet()
            template = StringRedisTemplate(connectionFactory)
            template.afterPropertiesSet()
            adapter = RedisRefreshTokenRotationAdapter(
                redisTemplate = template,
                clock = Clock.systemUTC(),
                namespace = NAMESPACE,
                issuer = ISSUER,
            )
        }

        @JvmStatic
        @AfterClass
        fun stopRedis() {
            if (::connectionFactory.isInitialized) connectionFactory.destroy()
            if (redisStarted) redis.stop()
        }
    }
}
