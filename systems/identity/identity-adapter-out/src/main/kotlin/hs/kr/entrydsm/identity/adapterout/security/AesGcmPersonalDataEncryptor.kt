package hs.kr.entrydsm.identity.adapterout.security

import hs.kr.entrydsm.identity.application.port.out.PersonalDataEncryptor
import hs.kr.entrydsm.common.crypto.PersonalDataCipher
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Component

@Component
@Lazy(false)
class AesGcmPersonalDataEncryptor(
    @Value("\${security.pii.encryption-key-base64}") keyBase64: String,
) : PersonalDataEncryptor {
    private val cipher = PersonalDataCipher(keyBase64)

    override fun encrypt(value: String): String = cipher.encrypt(value)

    override fun decrypt(value: String): String = cipher.decrypt(value)

    override fun isEncrypted(value: String): Boolean = cipher.isEncrypted(value)
}
