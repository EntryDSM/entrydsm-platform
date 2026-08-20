package hs.kr.entrydsm.identity.application.port.out

interface LoginIdHasher {
    fun hash(loginId: String): String

    fun isHash(value: String): Boolean
}
