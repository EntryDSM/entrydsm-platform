package hs.kr.entrydsm.identity.application.mock

import hs.kr.entrydsm.identity.application.port.out.AccountAlreadyExistsException
import hs.kr.entrydsm.identity.application.port.out.AccountRegistration
import hs.kr.entrydsm.identity.domain.enum.AccountStatus
import hs.kr.entrydsm.identity.domain.enum.ApplicantStatus
import hs.kr.entrydsm.identity.domain.enum.Role
import hs.kr.entrydsm.identity.domain.enum.SignupType
import hs.kr.entrydsm.identity.domain.model.PasswordHash
import hs.kr.entrydsm.identity.domain.model.StudentProfile
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class MockAuthAccountRepositoryAdapterTest {
    private val repository = MockAuthAccountRepositoryAdapter()

    @Test
    fun registerAndQueryUseTheAssignedUserIdAndLoginId() {
        val account = repository.register(registration("entry"), NOW)

        assertEquals(1L, account.userId)
        assertSame(account, repository.findByUserId(1L))
        assertSame(account, repository.findByLoginId("entry"))
    }

    @Test(expected = AccountAlreadyExistsException::class)
    fun registerRejectsDuplicateLoginIdAtomically() {
        repository.register(registration("entry"), NOW)

        repository.register(registration("entry"), NOW)
    }

    @Test
    fun saveAdvancesTheFallbackIdSequence() {
        val saved = hs.kr.entrydsm.identity.domain.model.Account.create(
            userId = 42L,
            loginId = "saved",
            passwordHash = PasswordHash.fromEncoded("hash"),
            role = Role.USER,
            status = AccountStatus.ACTIVE,
            profile = profile("saved"),
            createdAt = NOW,
            updatedAt = NOW,
        )

        repository.save(saved)

        assertEquals(43L, repository.register(registration("entry"), NOW).userId)
    }

    private fun registration(loginId: String): AccountRegistration = AccountRegistration(
        loginId = loginId,
        passwordHash = PasswordHash.fromEncoded("hash"),
        role = Role.USER,
        status = AccountStatus.ACTIVE,
        profile = profile(loginId),
    )

    private fun profile(loginId: String): StudentProfile = StudentProfile(
        name = loginId,
        phone = "01012345678",
        birthdate = java.time.LocalDate.of(2009, 3, 15),
        signupType = SignupType.SELF,
        applicantStatus = ApplicantStatus.NONE,
        updatedAt = NOW,
    )

    private companion object {
        val NOW: Instant = Instant.parse("2026-06-11T10:00:00Z")
    }
}
