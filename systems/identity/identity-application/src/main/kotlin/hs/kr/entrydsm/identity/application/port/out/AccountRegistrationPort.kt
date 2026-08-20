package hs.kr.entrydsm.identity.application.port.out

import hs.kr.entrydsm.identity.domain.model.Account
import java.time.Instant

/** Persists an account and its required application data as one atomic operation. */
fun interface AccountRegistrationPort {
    /** Returns the account after the persistence adapter has assigned its database ID. */
    fun register(registration: AccountRegistration, createdAt: Instant): Account
}
