package hs.kr.entrydsm.identity.application.port.out

interface PersonalDataEncryptor {
    fun encrypt(value: String): String

    fun decrypt(value: String): String

    fun isEncrypted(value: String): Boolean
}
