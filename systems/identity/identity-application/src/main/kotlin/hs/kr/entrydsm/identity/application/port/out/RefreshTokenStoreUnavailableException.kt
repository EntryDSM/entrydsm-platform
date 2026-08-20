package hs.kr.entrydsm.identity.application.port.out

/** Signals that refresh-token state could not be read or written in Redis. */
class RefreshTokenStoreUnavailableException(cause: Throwable) : RuntimeException(cause)
