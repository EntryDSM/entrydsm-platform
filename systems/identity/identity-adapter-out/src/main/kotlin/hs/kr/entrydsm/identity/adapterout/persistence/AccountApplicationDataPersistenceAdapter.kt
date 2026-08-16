package hs.kr.entrydsm.identity.adapterout.persistence

import hs.kr.entrydsm.identity.application.port.out.AccountRepository
import hs.kr.entrydsm.identity.application.port.out.ApplicationDataPort
import hs.kr.entrydsm.identity.application.port.out.data.ApplicationSnapshot
import hs.kr.entrydsm.identity.domain.enum.ApplicantStatus
import hs.kr.entrydsm.identity.domain.enum.ErrorCode
import hs.kr.entrydsm.identity.domain.exception.IdentityDomainException
import hs.kr.entrydsm.identity.domain.model.Account
import java.time.Instant
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/** Reads application state from the account aggregate persisted by the account adapter. */
@Component
@Primary
@Profile("!test")
class AccountApplicationDataPersistenceAdapter(
    private val accountRepository: AccountRepository,
) : ApplicationDataPort {
    override fun create(userId: Long, updatedAt: Instant): ApplicationSnapshot =
        findByUserId(userId) ?: throw IdentityDomainException(ErrorCode.USER_NOT_FOUND)

    override fun findByUserId(userId: Long): ApplicationSnapshot? =
        accountRepository.findByUserId(userId)?.toApplicationSnapshot()

    @Transactional
    override fun cancel(
        userId: Long,
        reason: String?,
        updatedAt: Instant,
    ): ApplicationSnapshot {
        val account = accountRepository.findByUserId(userId)
            ?: throw IdentityDomainException(ErrorCode.USER_NOT_FOUND)
        account.profile.cancel(updatedAt)
        accountRepository.save(account)
        return account.toApplicationSnapshot()
    }

    private fun Account.toApplicationSnapshot(
        applicantStatus: ApplicantStatus = profile.applicantStatus,
        updatedAt: Instant = profile.updatedAt,
    ): ApplicationSnapshot = ApplicationSnapshot(
        userId = userId,
        applicantStatus = applicantStatus,
        submittedAt = profile.submittedAt,
        updatedAt = updatedAt,
        passStatus = profile.passStatus,
        announcedAt = profile.announcedAt,
    )
}
