package hs.kr.entrydsm.identity.adapterout.security

import hs.kr.entrydsm.identity.application.port.out.RefreshTokenStoreUnavailableException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.any
import org.mockito.Mockito.anyString
import org.mockito.Mockito.eq
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.dao.DataAccessResourceFailureException
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ValueOperations

class RedisRefreshTokenRotationAdapterTest {
    private val redisTemplate = mock(StringRedisTemplate::class.java)
    @Suppress("UNCHECKED_CAST")
    private val valueOperations = mock(ValueOperations::class.java) as ValueOperations<String, String>
    private val adapter = RedisRefreshTokenRotationAdapter(
        redisTemplate = redisTemplate,
        clock = Clock.fixed(NOW, UTC),
        namespace = NAMESPACE,
        issuer = ISSUER,
    )

    init {
        `when`(redisTemplate.opsForValue()).thenReturn(valueOperations)
    }

    @Test
    fun consumesWithNxAndTokenExpiry() {
        `when`(
            valueOperations.setIfAbsent(
                CONSUMED_KEY,
                "1",
                TTL,
            )
        ).thenReturn(true)

        assertTrue(adapter.consume(TOKEN_ID, NOW.plusSeconds(30)))

        verify(valueOperations).setIfAbsent(CONSUMED_KEY, "1", TTL)
    }

    @Test
    fun expiredTokenIsNotStored() {
        assertFalse(adapter.consume(TOKEN_ID, NOW))

        org.mockito.Mockito.verifyNoInteractions(valueOperations)
    }

    @Test
    fun versionIsReadAndIncrementedForGlobalRevocation() {
        `when`(valueOperations.get(VERSION_KEY)).thenReturn("4")
        `when`(valueOperations.increment(VERSION_KEY)).thenReturn(5L)

        assertEquals(4L, adapter.currentVersion(USER_ID))
        adapter.revokeAll(USER_ID)

        verify(valueOperations).increment(VERSION_KEY)
    }

    @Test
    fun redisFailureIsMappedToStoreUnavailable() {
        val failure = DataAccessResourceFailureException("redis unavailable")
        `when`(
            valueOperations.setIfAbsent(
                anyString(),
                eq("1"),
                any(),
            )
        ).thenThrow(failure)

        val thrown = try {
            adapter.consume(TOKEN_ID, NOW.plusSeconds(30))
            null
        } catch (exception: RefreshTokenStoreUnavailableException) {
            exception
        }

        assertTrue(thrown != null)
        assertSame(failure, thrown?.cause)
    }

    @Test
    fun currentVersionRedisFailureIsMappedToStoreUnavailable() {
        val failure = DataAccessResourceFailureException("redis unavailable")
        `when`(valueOperations.get(anyString())).thenThrow(failure)

        val thrown = try {
            adapter.currentVersion(USER_ID)
            null
        } catch (exception: RefreshTokenStoreUnavailableException) {
            exception
        }

        assertSame(failure, thrown?.cause)
    }

    @Test
    fun revokeAllRedisFailureIsMappedToStoreUnavailable() {
        val failure = DataAccessResourceFailureException("redis unavailable")
        `when`(valueOperations.increment(anyString())).thenThrow(failure)

        val thrown = try {
            adapter.revokeAll(USER_ID)
            null
        } catch (exception: RefreshTokenStoreUnavailableException) {
            exception
        }

        assertSame(failure, thrown?.cause)
    }

    private companion object {
        const val NAMESPACE = "test"
        const val TOKEN_ID = "token-id"
        const val USER_ID = 123L
        val NOW: Instant = Instant.parse("2026-06-11T10:00:00Z")
        val UTC = ZoneOffset.UTC
        val TTL = java.time.Duration.ofSeconds(30)
        const val ISSUER = "entrydsm-identity"
        const val CONSUMED_KEY = "$NAMESPACE:$ISSUER:identity:auth:refresh:consumed:$TOKEN_ID"
        const val VERSION_KEY = "$NAMESPACE:$ISSUER:identity:auth:refresh:version:$USER_ID"
    }
}
