package hs.kr.entrydsm.identity.adapterout.persistence

import hs.kr.entrydsm.identity.application.port.out.AccountCommandPort
import hs.kr.entrydsm.identity.application.port.out.AccountRegistrationPort
import hs.kr.entrydsm.identity.application.port.out.ApplicationDataPort
import hs.kr.entrydsm.identity.domain.model.Account
import java.time.Instant
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class TransactionalAccountRegistrationAdapter(
    private val accountCommandPort: AccountCommandPort,
    private val applicationDataPort: ApplicationDataPort,
) : AccountRegistrationPort {
    @Transactional
    override fun register(account: Account, createdAt: Instant): Account {
        val savedAccount = accountCommandPort.save(account)
        applicationDataPort.create(savedAccount.userId, createdAt)
        return savedAccount
    }
}
