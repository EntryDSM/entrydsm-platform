package hs.kr.entrydsm.identity.application.mock

import hs.kr.entrydsm.identity.application.port.out.AccountAlreadyExistsException
import hs.kr.entrydsm.identity.application.port.out.AccountRegistration
import hs.kr.entrydsm.identity.application.port.out.AccountRepository
import hs.kr.entrydsm.identity.domain.model.Account
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/** Fallback account store that keeps the authentication service runnable without the account API. */
class MockAuthAccountRepositoryAdapter : AccountRepository {
    private val accountsById = ConcurrentHashMap<Long, Account>()
    private val userIdSequence = AtomicLong(0)

    override fun findByLoginId(loginId: String): Account? =
        accountsById.values.firstOrNull { it.loginId == loginId }

    override fun findByUserId(userId: Long): Account? = accountsById[userId]

    override fun save(account: Account): Account {
        accountsById[account.userId] = account
        userIdSequence.updateAndGet { current -> maxOf(current, account.userId) }
        return account
    }

    override fun register(registration: AccountRegistration, createdAt: Instant): Account {
        if (findByLoginId(registration.loginId) != null) {
            throw AccountAlreadyExistsException(
                IllegalStateException("Account login id is already registered."),
            )
        }

        val account = Account.create(
            userId = userIdSequence.incrementAndGet(),
            loginId = registration.loginId,
            passwordHash = registration.passwordHash,
            role = registration.role,
            status = registration.status,
            profile = registration.profile,
            createdAt = createdAt,
            updatedAt = createdAt,
        )
        accountsById[account.userId] = account
        return account
    }
}

@Configuration(proxyBeanMethods = false)
class MockAuthAccountRepositoryAdapterConfiguration {
    @Bean
    @ConditionalOnMissingBean(AccountRepository::class)
    fun mockAuthAccountRepositoryAdapter(): AccountRepository = MockAuthAccountRepositoryAdapter()
}
