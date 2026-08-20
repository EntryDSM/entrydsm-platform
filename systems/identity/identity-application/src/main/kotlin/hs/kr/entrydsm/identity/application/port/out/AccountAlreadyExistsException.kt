package hs.kr.entrydsm.identity.application.port.out

/** Signals that account registration violated the database login-id uniqueness constraint. */
class AccountAlreadyExistsException(cause: Throwable) : RuntimeException(cause)
