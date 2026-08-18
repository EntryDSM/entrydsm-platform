package hs.kr.entrydsm.identity.adapterout.security

import hs.kr.entrydsm.identity.application.port.out.PassProofStore
import hs.kr.entrydsm.identity.application.port.out.PassProofStoreUnavailableException
import hs.kr.entrydsm.identity.application.port.out.PassVerificationProof
import hs.kr.entrydsm.identity.application.port.out.PersonalDataEncryptor
import java.nio.charset.StandardCharsets.UTF_8
import java.security.MessageDigest
import java.time.Duration
import org.springframework.beans.factory.annotation.Value
import org.springframework.dao.DataAccessException
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component

/** Stores only encrypted PASS attributes and consumes each proof once. */
@Component
class RedisPassProofStoreAdapter(
    private val redisTemplate: StringRedisTemplate,
    private val personalDataEncryptor: PersonalDataEncryptor,
    @Value("\${identity.redis.namespace}") private val namespace: String,
) : PassProofStore {
    init {
        require(namespace.isNotBlank()) { "Redis key namespace must not be blank." }
    }

    override fun save(phoneNumber: String, name: String, ttlSeconds: Long) {
        require(phoneNumber.isNotBlank() && name.isNotBlank())
        require(ttlSeconds > 0) { "PASS proof TTL must be positive." }
        redis {
            redisTemplate.opsForValue().set(
                key(phoneNumber),
                listOf(
                    personalDataEncryptor.encrypt(phoneNumber),
                    personalDataEncryptor.encrypt(name),
                ).joinToString(VALUE_SEPARATOR),
                Duration.ofSeconds(ttlSeconds),
            )
        }
    }

    override fun consume(phoneNumber: String): PassVerificationProof? = redis {
        val stored = redisTemplate.opsForValue().getAndDelete(key(phoneNumber)) ?: return@redis null
        val separator = stored.indexOf(VALUE_SEPARATOR)
        if (separator <= 0 || separator == stored.lastIndex) return@redis null
        try {
            PassVerificationProof(
                phoneNumber = personalDataEncryptor.decrypt(stored.substring(0, separator)),
                name = personalDataEncryptor.decrypt(stored.substring(separator + 1)),
            ).takeIf { it.phoneNumber == phoneNumber && it.name.isNotBlank() }
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    private fun key(phoneNumber: String): String = "$namespace:identity:pass-proof:${sha256(phoneNumber)}"

    private fun sha256(value: String): String = MessageDigest
        .getInstance("SHA-256")
        .digest(value.toByteArray(UTF_8))
        .joinToString(EMPTY) { "%02x".format(it) }

    private fun <T> redis(action: () -> T): T = try {
        action()
    } catch (exception: DataAccessException) {
        throw PassProofStoreUnavailableException(exception)
    }

    private companion object {
        const val VALUE_SEPARATOR = "|"
        const val EMPTY = ""
    }
}
