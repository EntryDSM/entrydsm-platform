package hs.kr.entrydsm.identity.adapterout.security

import hs.kr.entrydsm.identity.application.port.out.RefreshTokenRotationStore
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
) : RefreshTokenRotationStore {
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

    private fun consumedKey(tokenId: String): String = "${keyPrefix}consumed:$tokenId"

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
    }
}
