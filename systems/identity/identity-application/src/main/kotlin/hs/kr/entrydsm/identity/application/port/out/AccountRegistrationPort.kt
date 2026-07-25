package hs.kr.entrydsm.identity.application.port.out

import hs.kr.entrydsm.identity.domain.model.Account
import java.time.Instant

/** Persists an account and its required application data as one atomic operation. */
fun interface AccountRegistrationPort {
    fun register(account: Account, createdAt: Instant): Account
}
