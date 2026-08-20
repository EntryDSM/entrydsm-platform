package hs.kr.entrydsm.observability.domain.service

import java.security.MessageDigest

/** 동일 오류를 그룹핑하기 위한 안정적인 짧은 해시(64비트)를 만든다. 짧을수록 서로 다른 오류가 한 그룹으로 합쳐질 위험이 커진다. */
object Fingerprint {
    fun of(vararg parts: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(parts.joinToString("|").toByteArray())
        return digest.joinToString("") { "%02x".format(it) }.take(16)
    }
}
