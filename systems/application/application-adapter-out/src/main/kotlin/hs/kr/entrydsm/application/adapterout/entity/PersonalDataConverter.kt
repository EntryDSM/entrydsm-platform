package hs.kr.entrydsm.application.adapterout.entity

import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter
import java.nio.charset.StandardCharsets.UTF_8
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
@Converter
class PersonalDataConverter(
    @Value("\${security.pii.encryption-key-base64}") keyBase64: String,
) : AttributeConverter<String?, String?> {
    private val key = SecretKeySpec(Base64.getDecoder().decode(keyBase64.trim()), "AES")
    private val secureRandom = SecureRandom()

    init {
        require(keyBase64.isNotBlank()) { "PII encryption key must not be blank" }
        require(key.encoded.size in setOf(16, 24, 32)) { "PII encryption key must be 128, 192, or 256 bits" }
    }

    override fun convertToDatabaseColumn(value: String?): String? = value?.let {
        val iv = ByteArray(12).also(secureRandom::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        "v1.${Base64.getUrlEncoder().withoutPadding().encodeToString(iv)}." +
            Base64.getUrlEncoder().withoutPadding().encodeToString(cipher.doFinal(it.toByteArray(UTF_8)))
    }

    override fun convertToEntityAttribute(value: String?): String? = value?.let {
        if (!it.startsWith("v1.")) return@let it
        val parts = it.split('.')
        require(parts.size == 3) { "Invalid encrypted personal data format" }
        val iv = Base64.getUrlDecoder().decode(parts[1])
        require(iv.size == 12) { "Invalid encrypted personal data IV" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        cipher.doFinal(Base64.getUrlDecoder().decode(parts[2])).toString(UTF_8)
    }
}
