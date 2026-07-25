package hs.kr.entrydsm.identity.adapterout.persistence

import hs.kr.entrydsm.identity.application.port.out.AccountQueryPort
import hs.kr.entrydsm.identity.application.port.out.AccountRepository
import hs.kr.entrydsm.identity.domain.model.Account
import org.springframework.stereotype.Component

@Component
class AccountQueryPersistenceAdapter(
    private val accountRepository: AccountRepository,
) : AccountQueryPort {
    override fun findByLoginId(loginId: String): Account? =
        accountRepository.findByLoginId(loginId)

    override fun findByUserId(userId: Long): Account? =
        accountRepository.findByUserId(userId)
}
