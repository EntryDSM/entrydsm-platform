package hs.kr.entrydsm.identity.application.port.out

import hs.kr.entrydsm.identity.domain.model.Account
import java.time.Instant

interface AccountRepository {
    fun findByLoginId(loginId: String): Account?

    fun findByUserId(userId: Long): Account?

    fun save(account: Account): Account

    fun register(registration: AccountRegistration, createdAt: Instant): Account
}
