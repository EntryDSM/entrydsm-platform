package hs.kr.entrydsm.identity.domain.policy

import java.nio.charset.StandardCharsets

object PasswordPolicy {
    const val MAX_UTF8_BYTES = 72

    fun isWithinBcryptLimit(password: String): Boolean =
        password.toByteArray(StandardCharsets.UTF_8).size <= MAX_UTF8_BYTES
}
