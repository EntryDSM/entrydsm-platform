package hs.kr.entrydsm.identity.application.port.out

import hs.kr.entrydsm.identity.domain.model.Account

/** CQRS command port for account writes. */
fun interface AccountCommandPort {
    fun save(account: Account): Account
}
