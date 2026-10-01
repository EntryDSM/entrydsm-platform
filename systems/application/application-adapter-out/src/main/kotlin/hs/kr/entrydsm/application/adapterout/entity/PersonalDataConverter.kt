package hs.kr.entrydsm.application.adapterout.entity

import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter
import hs.kr.entrydsm.common.crypto.PersonalDataCipher
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
@Converter
class PersonalDataConverter(
    @Value("\${security.pii.encryption-key-base64}") keyBase64: String,
) : AttributeConverter<String?, String?> {
    private val cipher = PersonalDataCipher(keyBase64)

    override fun convertToDatabaseColumn(value: String?): String? = value?.let {
        cipher.encrypt(it)
    }

    override fun convertToEntityAttribute(value: String?): String? = value?.let {
        if (cipher.isEncrypted(it)) cipher.decrypt(it) else it
    }
}
