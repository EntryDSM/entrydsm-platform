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
interface AccountRepository {
    fun findByLoginId(loginId: String): Account?

    fun findByUserId(userId: Long): Account?

    fun save(account: Account): Account

    fun register(registration: AccountRegistration, createdAt: Instant): Account
}
