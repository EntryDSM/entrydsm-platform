package hs.kr.entrydsm.identity.application.service

import hs.kr.entrydsm.identity.application.port.out.data.ApplicationSnapshot
import hs.kr.entrydsm.identity.domain.enum.AccountStatus
import hs.kr.entrydsm.identity.domain.enum.ApplicantStatus
import hs.kr.entrydsm.identity.domain.enum.PassStatus
import hs.kr.entrydsm.identity.domain.enum.SignupType
import hs.kr.entrydsm.identity.domain.model.Account
import hs.kr.entrydsm.identity.domain.model.StudentProfile
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class IdentityResultMapperTest {
    @Test
    fun accountMapsToAccountResult() {
        val account = mock(Account::class.java)
        val profile = mock(StudentProfile::class.java)
        `when`(account.userId).thenReturn(123L)
        `when`(account.role).thenReturn("USER")
        `when`(account.status).thenReturn(AccountStatus.ACTIVE)
        `when`(account.profile).thenReturn(profile)
        `when`(account.createdAt).thenReturn(NOW)
        `when`(account.updatedAt).thenReturn(NOW)
        `when`(profile.name).thenReturn("홍길동")
        `when`(profile.phone).thenReturn("01012345678")
        `when`(profile.birthdate).thenReturn(LocalDate.of(2009, 3, 15))
        `when`(profile.signupType).thenReturn(SignupType.SELF)
        `when`(profile.applicantStatus).thenReturn(ApplicantStatus.NONE)

        val result = account.toAccountResult()

        assertEquals(123L, result.userId)
        assertEquals("홍길동", result.profile.name)
        assertEquals(ApplicantStatus.NONE, result.profile.applicantStatus)
    }

    @Test
    fun applicationSnapshotMapsToStatusResult() {
        val snapshot = ApplicationSnapshot(
            userId = 123L,
            applicantStatus = ApplicantStatus.SUBMITTED,
            submittedAt = NOW,
            updatedAt = NOW,
            passStatus = PassStatus.NOT_ANNOUNCED,
            announcedAt = null,
        )

        val result = snapshot.toStatusResult()

        assertEquals(ApplicantStatus.SUBMITTED, result.applicantStatus)
        assertEquals(NOW, result.submittedAt)
        assertEquals(NOW, result.updatedAt)
    }

    private companion object {
        val NOW: Instant = Instant.parse("2026-06-11T10:00:00Z")
    }
}
