package hs.kr.entrydsm.identity.application.port.out

interface PassCallbackTokenStore {
    /** Claims a provider callback token once until its short TTL expires. */
    fun claim(token: String, ttlSeconds: Long): Boolean
}
