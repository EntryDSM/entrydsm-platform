package hs.kr.entrydsm.identity.application.port.out

import hs.kr.entrydsm.identity.domain.model.Account
import java.time.Instant

/** CQRS command port for account writes. */
interface AccountCommandPort {
    fun save(account: Account): Account

    fun register(registration: AccountRegistration, createdAt: Instant): Account
}
