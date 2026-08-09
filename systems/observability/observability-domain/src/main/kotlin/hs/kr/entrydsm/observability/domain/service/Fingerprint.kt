package hs.kr.entrydsm.observability.domain.service

import java.security.MessageDigest

/** 동일 오류를 그룹핑하기 위한 안정적인 짧은 해시를 만든다. */
object Fingerprint {
    fun of(vararg parts: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(parts.joinToString("|").toByteArray())
        return digest.joinToString("") { "%02x".format(it) }.take(8)
    }
}
