package hs.kr.entrydsm.application.application.port.out

fun interface AccountPhoneValidator {
    fun validate(accountId: Long, phoneNumber: String): Boolean
}
