package hs.kr.entrydsm.identity.application.port.out

import hs.kr.entrydsm.identity.domain.model.Account

/** CQRS query port for account reads. */
interface AccountQueryPort {
    fun findByLoginId(loginId: String): Account?

    fun findByUserId(userId: Long): Account?
}
