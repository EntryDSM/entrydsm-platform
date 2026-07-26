package hs.kr.entrydsm.identity.adapterout.persistence

import hs.kr.entrydsm.identity.application.port.out.AccountCommandPort
import hs.kr.entrydsm.identity.application.port.out.AccountRegistration
import hs.kr.entrydsm.identity.application.port.out.AccountRepository
import hs.kr.entrydsm.identity.domain.model.Account
import java.time.Instant
import org.springframework.stereotype.Component

@Component
class AccountCommandPersistenceAdapter(
    private val accountRepository: AccountRepository,
) : AccountCommandPort {
    override fun save(account: Account): Account =
        accountRepository.save(account)

    override fun register(registration: AccountRegistration, createdAt: Instant): Account =
        accountRepository.register(registration, createdAt)
}
