package hs.kr.entrydsm.application.application.exception

class AccountPhoneLookupFailedException(cause: Throwable) : RuntimeException("account phone lookup failed", cause)
