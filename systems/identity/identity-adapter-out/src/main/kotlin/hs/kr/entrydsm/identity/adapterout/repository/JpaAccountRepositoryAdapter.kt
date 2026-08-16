package hs.kr.entrydsm.identity.adapterout.repository

import hs.kr.entrydsm.identity.adapterout.entity.AccountJpaEntity
import hs.kr.entrydsm.identity.adapterout.entity.StudentProfileJpaEntity
import hs.kr.entrydsm.identity.application.port.out.AccountRegistration
import hs.kr.entrydsm.identity.application.port.out.AccountRepository
import hs.kr.entrydsm.identity.domain.model.Account
import hs.kr.entrydsm.identity.domain.model.StudentProfile
import java.time.Instant
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import org.springframework.context.annotation.Profile

@Repository
@Transactional
@Profile("!test")
class JpaAccountRepositoryAdapter(
    private val accountJpaRepository: AccountJpaRepository,
    private val studentProfileJpaRepository: StudentProfileJpaRepository,
) : AccountRepository {
    @Transactional(readOnly = true)
    override fun findByLoginId(loginId: String): Account? =
        accountJpaRepository.findByLoginIdHash(loginId)?.toDomain()

    @Transactional(readOnly = true)
    override fun findByUserId(userId: Long): Account? =
        accountJpaRepository.findById(userId).orElse(null)?.toDomain()

    override fun save(account: Account): Account {
        val existing = accountJpaRepository.findById(account.userId).orElse(null)
            ?.takeIf { it.loginIdHash == account.loginId }
        val entity = if (existing == null) {
            AccountJpaEntity(
                loginIdHash = account.loginId,
                passwordHash = account.passwordHash.value,
                role = account.role,
                status = account.status,
            )
        } else {
            existing.apply {
                passwordHash = account.passwordHash.value
                status = account.status
            }
        }
        val savedAccount = accountJpaRepository.saveAndFlush(entity)
        val savedUserId = requireNotNull(savedAccount.id)
        val profile = studentProfileJpaRepository.findByAccount_Id(savedUserId)
            ?: StudentProfileJpaEntity(
                account = savedAccount,
                signupType = account.profile.signupType,
                nameEncrypted = account.profile.name,
                phoneEncrypted = account.profile.phone,
                birthdate = account.profile.birthdate,
            )
        profile.applicantStatus = account.profile.applicantStatus
        profile.passStatus = account.profile.passStatus
        profile.submittedAt = account.profile.submittedAt
        profile.announcedAt = account.profile.announcedAt
        val savedProfile = studentProfileJpaRepository.saveAndFlush(profile)
        return savedAccount.toDomain(savedProfile)
    }

    override fun register(registration: AccountRegistration, createdAt: Instant): Account {
        val savedAccount = accountJpaRepository.saveAndFlush(
            AccountJpaEntity(
                loginIdHash = registration.loginId,
                passwordHash = registration.passwordHash.value,
                role = registration.role,
                status = registration.status,
            ),
        )
        val savedProfile = studentProfileJpaRepository.saveAndFlush(
            StudentProfileJpaEntity(
                account = savedAccount,
                signupType = registration.profile.signupType,
                nameEncrypted = registration.profile.name,
                phoneEncrypted = registration.profile.phone,
                birthdate = registration.profile.birthdate,
                submittedAt = registration.profile.submittedAt,
                applicantStatus = registration.profile.applicantStatus,
                passStatus = registration.profile.passStatus,
                announcedAt = registration.profile.announcedAt,
            ),
        )
        return savedAccount.toDomain(savedProfile)
    }

    private fun AccountJpaEntity.toDomain(profile: StudentProfileJpaEntity? = null): Account {
        val resolvedProfile = profile ?: requireNotNull(id) { "Account ID must be present" }
            .let { studentProfileJpaRepository.findByAccount_Id(it) }
            ?: error("Student profile not found for account $id")
        val createdAt = createdAtValue() ?: Instant.EPOCH
        val updatedAt = updatedAtValue() ?: createdAt
        return Account.create(
            userId = requireNotNull(id),
            loginId = loginIdHash,
            passwordHash = hs.kr.entrydsm.identity.domain.model.PasswordHash.fromEncoded(passwordHash),
            role = role,
            status = status,
            profile = resolvedProfile.toDomain(updatedAt),
            createdAt = createdAt,
            updatedAt = updatedAt,
        )
    }

    private fun StudentProfileJpaEntity.toDomain(accountUpdatedAt: Instant): StudentProfile =
        StudentProfile(
            name = nameEncrypted,
            phone = phoneEncrypted,
            birthdate = birthdate,
            signupType = signupType,
            applicantStatus = applicantStatus,
            passStatus = passStatus,
            submittedAt = submittedAt,
            announcedAt = announcedAt,
            updatedAt = updatedAtValue() ?: accountUpdatedAt,
        )
}
