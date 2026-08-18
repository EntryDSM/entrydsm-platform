package hs.kr.entrydsm.identity.adapterout.security

import hs.kr.entrydsm.identity.application.port.out.PassProofStore
import hs.kr.entrydsm.identity.application.port.out.PassProofStoreUnavailableException
import hs.kr.entrydsm.identity.application.port.out.PassVerificationProof
import hs.kr.entrydsm.identity.application.port.out.PersonalDataEncryptor
import java.nio.charset.StandardCharsets.UTF_8
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import org.springframework.beans.factory.annotation.Value
import org.springframework.dao.DataAccessException
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
import org.springframework.stereotype.Component

/** Atomically stores encrypted PASS proof and consumes it only for a matching identity. */
@Component
class RedisPassProofStoreAdapter(
    private val redisTemplate: StringRedisTemplate,
    private val personalDataEncryptor: PersonalDataEncryptor,
    @Value("\${identity.redis.namespace}") private val namespace: String,
    @Value("\${pass.proof-key-current}") currentKey: String,
    @Value("\${pass.proof-key-previous:}") previousKey: String,
) : PassProofStore {
    private val currentSecret = secret(currentKey, "current")
    private val previousSecret = previousKey.trim().takeIf(String::isNotEmpty)?.let { secret(it, "previous") }

    init {
        require(namespace.isNotBlank()) { "Redis key namespace must not be blank." }
    }

    override fun saveForToken(token: String, phoneNumber: String, name: String, ttlSeconds: Long): Boolean {
        require(token.isNotBlank() && phoneNumber.isNotBlank() && name.isNotBlank())
        require(ttlSeconds > 0) { "PASS proof TTL must be positive." }
        return redis {
            val serialized = serialize(phoneNumber, name, currentSecret)
            redisTemplate.execute(
                SAVE_SCRIPT,
                listOf(callbackKey(token), proofKey(phoneNumber, currentSecret)),
                ttlSeconds.toString(),
                serialized,
            ) == SAVED
        }
    }

    override fun consume(phoneNumber: String, name: String): PassVerificationProof? = redis {
        require(phoneNumber.isNotBlank() && name.isNotBlank())
        consumeWithKey(phoneNumber, name, currentSecret)
            ?: previousSecret?.let { consumeWithKey(phoneNumber, name, it) }
    }

    private fun consumeWithKey(
        phoneNumber: String,
        name: String,
        secret: SecretKeySpec,
    ): PassVerificationProof? {
        val expectedTag = bindingTag(phoneNumber, name, secret)
        val stored = redisTemplate.execute(
            CONSUME_SCRIPT,
            listOf(proofKey(phoneNumber, secret)),
            expectedTag,
        ) ?: return null
        if (stored.isEmpty() || stored == MISMATCH) return null
        return try {
            val firstSeparator = stored.indexOf(VALUE_SEPARATOR)
            val secondSeparator = stored.indexOf(VALUE_SEPARATOR, firstSeparator + 1)
            if (firstSeparator <= 0 || secondSeparator <= firstSeparator) return null
            PassVerificationProof(
                phoneNumber = personalDataEncryptor.decrypt(
                    stored.substring(firstSeparator + 1, secondSeparator),
                ),
                name = personalDataEncryptor.decrypt(stored.substring(secondSeparator + 1)),
            ).takeIf { it.phoneNumber == phoneNumber && it.name == name }
        } catch (_: RuntimeException) {
            null
        }
    }

    private fun serialize(phoneNumber: String, name: String, secret: SecretKeySpec): String = listOf(
        bindingTag(phoneNumber, name, secret),
        personalDataEncryptor.encrypt(phoneNumber),
        personalDataEncryptor.encrypt(name),
    ).joinToString(VALUE_SEPARATOR)

    private fun proofKey(phoneNumber: String, secret: SecretKeySpec): String =
        "$namespace:identity:pass-proof:${hmac(phoneNumber, secret)}"

    private fun callbackKey(token: String): String =
        "$namespace:identity:pass-callback:${sha256(token)}"

    private fun bindingTag(phoneNumber: String, name: String, secret: SecretKeySpec): String =
        hmac("$phoneNumber$BINDING_SEPARATOR$name", secret)

    private fun hmac(value: String, secret: SecretKeySpec): String = Mac
        .getInstance(HMAC_ALGORITHM)
        .apply { init(secret) }
        .doFinal(value.toByteArray(UTF_8))
        .joinToString(EMPTY) { "%02x".format(it) }

    private fun sha256(value: String): String = MessageDigest
        .getInstance(SHA256_ALGORITHM)
        .digest(value.toByteArray(UTF_8))
        .joinToString(EMPTY) { "%02x".format(it) }

    private fun secret(value: String, label: String): SecretKeySpec {
        require(value.isNotBlank()) { "PASS proof $label key must not be blank." }
        return SecretKeySpec(value.toByteArray(UTF_8), HMAC_ALGORITHM)
    }

    private fun <T> redis(action: () -> T): T = try {
        action()
    } catch (exception: DataAccessException) {
        throw PassProofStoreUnavailableException(exception)
    }

    private companion object {
        const val HMAC_ALGORITHM = "HmacSHA256"
        const val SHA256_ALGORITHM = "SHA-256"
        const val VALUE_SEPARATOR = "|"
        const val BINDING_SEPARATOR = "\u0000"
        const val EMPTY = ""
        const val MISMATCH = "__PASS_PROOF_MISMATCH__"
        const val SAVED = 1L
        val SAVE_SCRIPT = DefaultRedisScript(
            """
            if redis.call('EXISTS', KEYS[1]) == 1 then
                return 0
            end
            redis.call('SET', KEYS[1], '1', 'EX', ARGV[1])
            redis.call('SET', KEYS[2], ARGV[2], 'EX', ARGV[1])
            return 1
            """.trimIndent(),
            Long::class.java,
        )
        val CONSUME_SCRIPT = DefaultRedisScript(
            """
            local value = redis.call('GET', KEYS[1])
            if not value then
                return ''
            end
            local separator = string.find(value, '|', 1, true)
            if not separator or string.sub(value, 1, separator - 1) ~= ARGV[1] then
                return '$MISMATCH'
            end
            redis.call('DEL', KEYS[1])
            return value
            """.trimIndent(),
            String::class.java,
        )
    }
}
