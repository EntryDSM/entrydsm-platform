package hs.kr.entrydsm.identity.adapterout.persistence

import hs.kr.entrydsm.identity.application.port.out.AccountQueryPort
import hs.kr.entrydsm.identity.application.port.out.AccountRepository
import hs.kr.entrydsm.identity.domain.model.Account
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

@Component
@Primary
@Profile("prod", "dev", "integration")
class AccountQueryPersistenceAdapter(
    private val accountRepository: AccountRepository,
) : AccountQueryPort {
    override fun findByLoginId(loginId: String): Account? =
        accountRepository.findByLoginId(loginId)

    override fun findByUserId(userId: Long): Account? =
        accountRepository.findByUserId(userId)
}
