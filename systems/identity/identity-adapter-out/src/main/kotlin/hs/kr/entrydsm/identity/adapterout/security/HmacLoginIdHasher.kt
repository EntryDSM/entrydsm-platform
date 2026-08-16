package hs.kr.entrydsm.identity.adapterout.security

import hs.kr.entrydsm.identity.application.port.out.LoginIdHasher
import java.nio.charset.StandardCharsets.UTF_8
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class HmacLoginIdHasher(
    @Value("\${security.pii.login-id-hash-key:dev-identity-login-id-hash-key-change-me}") key: String,
) : LoginIdHasher {
    private val secretKey = SecretKeySpec(key.toByteArray(UTF_8), ALGORITHM)

    override fun hash(loginId: String): String {
        require(loginId.isNotBlank()) { "Login ID must not be blank" }
        return Mac.getInstance(ALGORITHM).run {
            init(secretKey)
            doFinal(loginId.toByteArray(UTF_8)).toHex()
        }
    }

    override fun isHash(value: String): Boolean =
        value.length == HASH_LENGTH && value.all { it in HEX_DIGITS }

    private fun ByteArray.toHex(): String = joinToString(separator = "") { byte ->
        "%02x".format(byte.toInt() and 0xff)
    }

    private companion object {
        const val ALGORITHM = "HmacSHA256"
        const val HASH_LENGTH = 64
        const val HEX_DIGITS = "0123456789abcdef"
    }
}
