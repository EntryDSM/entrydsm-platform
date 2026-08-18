package hs.kr.entrydsm.identity.adapterout.security

import hs.kr.entrydsm.identity.application.port.out.PassCallbackTokenStore
import hs.kr.entrydsm.identity.application.port.out.PassProofStoreUnavailableException
import java.nio.charset.StandardCharsets.UTF_8
import java.security.MessageDigest
import java.time.Duration
import org.springframework.beans.factory.annotation.Value
import org.springframework.dao.DataAccessException
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component

/** Prevents a KCB model token from being verified more than once. */
@Component
class RedisPassCallbackTokenStoreAdapter(
    private val redisTemplate: StringRedisTemplate,
    @Value("\${identity.redis.namespace}") private val namespace: String,
) : PassCallbackTokenStore {
    init {
        require(namespace.isNotBlank()) { "Redis key namespace must not be blank." }
    }

    override fun claim(token: String, ttlSeconds: Long): Boolean = try {
        require(token.isNotBlank() && ttlSeconds > 0)
        redisTemplate.opsForValue().setIfAbsent(
            key(token),
            CLAIMED_VALUE,
            Duration.ofSeconds(ttlSeconds),
        ) == true
    } catch (exception: DataAccessException) {
        throw PassProofStoreUnavailableException(exception)
    }

    private fun key(token: String): String = "$namespace:identity:pass-callback:${sha256(token)}"

    private fun sha256(value: String): String = MessageDigest
        .getInstance("SHA-256")
        .digest(value.toByteArray(UTF_8))
        .joinToString(EMPTY) { "%02x".format(it) }

    private companion object {
        const val CLAIMED_VALUE = "1"
        const val EMPTY = ""
    }
}
