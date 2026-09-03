package hs.kr.entrydsm.identity.adapterout.security

import hs.kr.entrydsm.identity.test.IntegrationTestGate
import hs.kr.entrydsm.identity.application.port.out.RefreshTokenStoreUnavailableException
import hs.kr.entrydsm.identity.application.port.out.PersonalDataEncryptor
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
import org.junit.Assert.assertEquals
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

    @Test
    fun passProofIsStoredOnceAndConsumedOnlyForMatchingName() {
        val token = "pass-token"
        val phone = "01012345678"
        assertTrue(passAdapter.saveForToken(token, phone, "홍길동", 30))
        assertFalse(passAdapter.saveForToken(token, phone, "홍길동", 30))
        assertFalse(passAdapter.consume(phone, "다른 이름") != null)

        val proof = passAdapter.consume(phone, "홍길동")

        assertEquals(phone, proof?.phoneNumber)
        assertEquals("홍길동", proof?.name)
        assertFalse(passAdapter.consume(phone, "홍길동") != null)
    }

    @Test
    fun passProofSupportsPreviousHmacKeyDuringRotation() {
        val oldAdapter = RedisPassProofStoreAdapter(
            redisTemplate = template,
            personalDataEncryptor = encryptor,
            namespace = NAMESPACE,
            currentKey = "old-proof-key",
            previousKey = "",
        )
        val rotatedAdapter = RedisPassProofStoreAdapter(
            redisTemplate = template,
            personalDataEncryptor = encryptor,
            namespace = NAMESPACE,
            currentKey = "new-proof-key",
            previousKey = "old-proof-key",
        )
        assertTrue(oldAdapter.saveForToken("rotation-token", "01098765432", "김철수", 30))

        val proof = rotatedAdapter.consume("01098765432", "김철수")

        assertEquals("김철수", proof?.name)
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
        lateinit var passAdapter: RedisPassProofStoreAdapter
        lateinit var encryptor: PersonalDataEncryptor
        var redisStarted = false

        @JvmStatic
        @BeforeClass
        fun startRedis() {
            val available = DockerClientFactory.instance().isDockerAvailable
            if (!available && IntegrationTestGate.isRequired()) {
                error("Docker daemon is required for Redis integration tests")
            }
            assumeTrue("Docker daemon is required for Redis integration tests", available)
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
            encryptor = AesGcmPersonalDataEncryptor(
                keyBase64 = "MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDE=",
            )
            passAdapter = RedisPassProofStoreAdapter(
                redisTemplate = template,
                personalDataEncryptor = encryptor,
                namespace = NAMESPACE,
                currentKey = "current-proof-key",
                previousKey = "",
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
