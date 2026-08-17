package hs.kr.entrydsm.identity.adapterout.security

import hs.kr.entrydsm.identity.application.port.out.PersonalDataEncryptor
import java.nio.charset.StandardCharsets.UTF_8
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Component

@Component
@Lazy(false)
class AesGcmPersonalDataEncryptor(
    @Value("\${security.pii.encryption-key-base64}") keyBase64: String,
) : PersonalDataEncryptor {
    private val key = SecretKeySpec(
        Base64.getDecoder().decode(keyBase64.trim()),
        KEY_ALGORITHM,
    )
    private val secureRandom = SecureRandom()

    init {
        require(keyBase64.isNotBlank()) {
            "PII encryption key must not be blank"
        }
        require(key.encoded.size in VALID_KEY_LENGTHS) {
            "PII encryption key must be 128, 192, or 256 bits"
        }
    }

    override fun encrypt(value: String): String {
        val iv = ByteArray(IV_LENGTH_BYTES).also(secureRandom::nextBytes)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_LENGTH_BITS, iv))
        val ciphertext = cipher.doFinal(value.toByteArray(UTF_8))
        return listOf(
            FORMAT_VERSION,
            Base64.getUrlEncoder().withoutPadding().encodeToString(iv),
            Base64.getUrlEncoder().withoutPadding().encodeToString(ciphertext),
        ).joinToString(".")
    }

    override fun decrypt(value: String): String {
        require(isEncrypted(value)) { "Plaintext personal data is not supported" }

        val parts = value.split('.')
        require(parts.size == 3) { "Invalid encrypted personal data format" }
        val iv = Base64.getUrlDecoder().decode(parts[1])
        val ciphertext = Base64.getUrlDecoder().decode(parts[2])
        require(iv.size == IV_LENGTH_BYTES) { "Invalid encrypted personal data IV" }

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_LENGTH_BITS, iv))
        return cipher.doFinal(ciphertext).toString(UTF_8)
    }

    override fun isEncrypted(value: String): Boolean = value.startsWith("$FORMAT_VERSION.")

    private companion object {
        const val KEY_ALGORITHM = "AES"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val FORMAT_VERSION = "v1"
        const val IV_LENGTH_BYTES = 12
        const val TAG_LENGTH_BITS = 128
        val VALID_KEY_LENGTHS = setOf(16, 24, 32)
    }
}
