package hs.kr.entrydsm.identity.application.port.out

import hs.kr.entrydsm.identity.domain.model.Account
import java.time.Instant

/**
 * Persistence contract for accounts.
 *
 * Implementations must enforce a database unique constraint on the login identifier for
 * [register]. A pre-registration lookup is only an optimization and must not be used as
 * the uniqueness guarantee.
 */
interface AccountRepository : AccountQueryPort, AccountCommandPort {

    override fun register(registration: AccountRegistration, createdAt: Instant): Account
}
