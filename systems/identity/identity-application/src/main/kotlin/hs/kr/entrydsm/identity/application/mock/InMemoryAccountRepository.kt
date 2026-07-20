package hs.kr.entrydsm.identity.application.mock

import hs.kr.entrydsm.identity.application.port.out.AccountRepository
import hs.kr.entrydsm.identity.domain.Account
import hs.kr.entrydsm.identity.domain.AccountStatus
import hs.kr.entrydsm.identity.domain.ApplicantStatus
import hs.kr.entrydsm.identity.domain.PassStatus
import hs.kr.entrydsm.identity.domain.SignupType
import hs.kr.entrydsm.identity.domain.StudentProfile
import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap
import org.springframework.stereotype.Component

@Component
class InMemoryAccountRepository : AccountRepository {
    private val accounts = ConcurrentHashMap<Long, Account>()

    init {
        val now = Instant.parse("2026-06-11T10:00:00Z")
        accounts[123L] = Account(
            userId = 123L,
            loginId = "01012345678",
            password = "Password1!",
            role = "USER",
            status = AccountStatus.ACTIVE,
            profile = StudentProfile(
                name = "홍길동",
                phone = "01012345678",
                birthdate = LocalDate.parse("2009-03-15"),
                signupType = SignupType.SELF,
                applicantStatus = ApplicantStatus.SUBMITTED,
                passStatus = PassStatus.PASSED,
                submittedAt = now,
                announcedAt = now,
                updatedAt = now,
            ),
            createdAt = now,
            updatedAt = now,
        )
    }

    override fun findByLoginId(loginId: String): Account? =
        accounts.values.firstOrNull { it.loginId == loginId }

    override fun findByUserId(userId: Long): Account? = accounts[userId]

    override fun save(account: Account): Account {
        accounts[account.userId] = account
        return account
    }
}
