package hs.kr.entrydsm.identity.adapterout.security

import hs.kr.entrydsm.identity.application.port.out.RefreshTokenRotationStore
import hs.kr.entrydsm.identity.application.port.out.RefreshTokenRevocationStore
import hs.kr.entrydsm.identity.application.port.out.RefreshTokenStoreUnavailableException
import java.time.Clock
import java.time.Duration
import java.time.Instant
import org.springframework.beans.factory.annotation.Value
import org.springframework.dao.DataAccessException
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component

/** Atomically consumes refresh token identifiers in Redis until their expiry. */
@Component
class RedisRefreshTokenRotationAdapter(
    private val redisTemplate: StringRedisTemplate,
    private val clock: Clock,
    @Value("\${identity.redis.namespace}")
    private val namespace: String,
    @Value("\${auth.jwt.issuer}")
    private val issuer: String,
) : RefreshTokenRotationStore, RefreshTokenRevocationStore {
    init {
        require(namespace.isNotBlank()) { "Redis key namespace must not be blank." }
        require(issuer.isNotBlank()) { "JWT issuer must not be blank." }
    }

    override fun consume(tokenId: String, expiresAt: Instant): Boolean {
        val ttl = Duration.between(Instant.now(clock), expiresAt)
        if (ttl.isNegative || ttl.isZero) return false

        return redis {
            redisTemplate.opsForValue().setIfAbsent(
                consumedKey(tokenId),
                CONSUMED_VALUE,
                ttl,
            ) == true
        }
    }

    override fun currentVersion(userId: Long): Long = redis {
        redisTemplate.opsForValue().get(versionKey(userId))?.toLongOrNull() ?: INITIAL_VERSION
    }

    override fun revokeAll(userId: Long) {
        redis {
            redisTemplate.opsForValue().increment(versionKey(userId))
                ?: error("Redis did not return the new refresh token version.")
        }
    }

    private fun consumedKey(tokenId: String): String = "${keyPrefix}consumed:$tokenId"

    private fun versionKey(userId: Long): String = "${keyPrefix}version:$userId"

    private val keyPrefix: String
        get() = "$namespace:$issuer:$SERVICE_NAME:auth:refresh:"

    private fun <T> redis(action: () -> T): T = try {
        action()
    } catch (exception: DataAccessException) {
        throw RefreshTokenStoreUnavailableException(exception)
    }

    private companion object {
        const val SERVICE_NAME = "identity"
        const val CONSUMED_VALUE = "1"
        const val INITIAL_VERSION = 0L
    }
}
