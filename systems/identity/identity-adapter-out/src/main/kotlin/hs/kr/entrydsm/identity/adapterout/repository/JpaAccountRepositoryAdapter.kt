package hs.kr.entrydsm.identity.adapterout.repository

import hs.kr.entrydsm.identity.adapterout.entity.AccountJpaEntity
import hs.kr.entrydsm.identity.adapterout.entity.StudentProfileJpaEntity
import hs.kr.entrydsm.identity.application.port.out.AccountRegistration
import hs.kr.entrydsm.identity.application.port.out.AccountRepository
import hs.kr.entrydsm.identity.application.port.out.LoginIdHasher
import hs.kr.entrydsm.identity.application.port.out.PersonalDataEncryptor
import hs.kr.entrydsm.identity.domain.model.Account
import hs.kr.entrydsm.identity.domain.model.StudentProfile
import java.time.Instant
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import org.springframework.context.annotation.Profile

@Repository
@Transactional
@Profile("prod", "dev", "integration")
class JpaAccountRepositoryAdapter(
    private val accountJpaRepository: AccountJpaRepository,
    private val studentProfileJpaRepository: StudentProfileJpaRepository,
    private val loginIdHasher: LoginIdHasher,
    private val personalDataEncryptor: PersonalDataEncryptor,
    @Value("\${security.pii.legacy-plaintext-read-enabled:true}") private val legacyPlaintextReadEnabled: Boolean,
) : AccountRepository {
    override fun findByLoginId(loginId: String): Account? {
        val hashedLoginId = loginIdHasher.hash(loginId)
        val entity = accountJpaRepository.findByLoginIdHash(hashedLoginId)
            ?: if (legacyPlaintextReadEnabled) {
                accountJpaRepository.findByLoginIdHash(loginId)
            } else {
                null
            }
        return entity?.let { it.toDomainAfterLegacyMigration() }
    }

    override fun findByUserId(userId: Long): Account? =
        accountJpaRepository.findById(userId).orElse(null)?.toDomainAfterLegacyMigration()

    override fun save(account: Account): Account {
        val hashedLoginId = loginIdHasher.hash(account.loginId)
        val existing = accountJpaRepository.findById(account.userId).orElse(null)
            ?.takeIf { it.loginIdHash == account.loginId || it.loginIdHash == hashedLoginId }
        val entity = if (existing == null) {
            AccountJpaEntity(
                loginIdHash = hashedLoginId,
                loginIdEncrypted = personalDataEncryptor.encrypt(account.loginId),
                passwordHash = account.passwordHash.value,
                role = account.role,
                status = account.status,
            )
        } else {
            existing.apply {
                loginIdHash = hashedLoginId
                loginIdEncrypted = personalDataEncryptor.encrypt(account.loginId)
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
                nameEncrypted = "",
                phoneEncrypted = "",
                birthdate = account.profile.birthdate,
            )
        profile.nameEncrypted = personalDataEncryptor.encrypt(account.profile.name)
        profile.phoneEncrypted = personalDataEncryptor.encrypt(account.profile.phone)
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
                loginIdHash = loginIdHasher.hash(registration.loginId),
                loginIdEncrypted = personalDataEncryptor.encrypt(registration.loginId),
                passwordHash = registration.passwordHash.value,
                role = registration.role,
                status = registration.status,
            ),
        )
        val savedProfile = studentProfileJpaRepository.saveAndFlush(
            StudentProfileJpaEntity(
                account = savedAccount,
                signupType = registration.profile.signupType,
                nameEncrypted = personalDataEncryptor.encrypt(registration.profile.name),
                phoneEncrypted = personalDataEncryptor.encrypt(registration.profile.phone),
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
            loginId = requireNotNull(loginIdEncrypted) { "Encrypted login ID must be present" }
                .let(personalDataEncryptor::decrypt),
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
            name = personalDataEncryptor.decrypt(nameEncrypted),
            phone = personalDataEncryptor.decrypt(phoneEncrypted),
            birthdate = birthdate,
            signupType = signupType,
            applicantStatus = applicantStatus,
            passStatus = passStatus,
            submittedAt = submittedAt,
            announcedAt = announcedAt,
            updatedAt = updatedAtValue() ?: accountUpdatedAt,
        )

    private fun AccountJpaEntity.toDomainAfterLegacyMigration(): Account {
        val profile = requireNotNull(id) { "Account ID must be present" }
            .let { studentProfileJpaRepository.findByAccount_Id(it) }
            ?: error("Student profile not found for account $id")
        migrateLegacyData(this, profile)
        return toDomain(profile)
    }

    private fun migrateLegacyData(
        account: AccountJpaEntity,
        profile: StudentProfileJpaEntity,
    ) {
        if (!legacyPlaintextReadEnabled) {
            check(loginIdHasher.isHash(account.loginIdHash)) {
                "Legacy plaintext login ID is disabled"
            }
            check(personalDataEncryptor.isEncrypted(account.loginIdEncrypted.orEmpty())) {
                "Legacy plaintext login ID encryption is disabled"
            }
            check(personalDataEncryptor.isEncrypted(profile.nameEncrypted)) {
                "Legacy plaintext name is disabled"
            }
            check(personalDataEncryptor.isEncrypted(profile.phoneEncrypted)) {
                "Legacy plaintext phone is disabled"
            }
            return
        }

        var accountChanged = false
        if (!loginIdHasher.isHash(account.loginIdHash)) {
            if (account.loginIdEncrypted == null) {
                account.loginIdEncrypted = personalDataEncryptor.encrypt(account.loginIdHash)
            }
            account.loginIdHash = loginIdHasher.hash(account.loginIdHash)
            accountChanged = true
        }
        if (!personalDataEncryptor.isEncrypted(account.loginIdEncrypted.orEmpty())) {
            account.loginIdEncrypted = personalDataEncryptor.encrypt(
                requireNotNull(account.loginIdEncrypted) { "Legacy login ID must be present" },
            )
            accountChanged = true
        }
        var profileChanged = false
        if (!personalDataEncryptor.isEncrypted(profile.nameEncrypted)) {
            profile.nameEncrypted = personalDataEncryptor.encrypt(profile.nameEncrypted)
            profileChanged = true
        }
        if (!personalDataEncryptor.isEncrypted(profile.phoneEncrypted)) {
            profile.phoneEncrypted = personalDataEncryptor.encrypt(profile.phoneEncrypted)
            profileChanged = true
        }
        if (accountChanged) accountJpaRepository.saveAndFlush(account)
        if (profileChanged) studentProfileJpaRepository.saveAndFlush(profile)
    }
}
