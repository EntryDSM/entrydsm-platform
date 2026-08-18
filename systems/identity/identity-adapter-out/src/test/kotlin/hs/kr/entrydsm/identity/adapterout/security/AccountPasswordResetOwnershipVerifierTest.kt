package hs.kr.entrydsm.identity.adapterout.security

import hs.kr.entrydsm.identity.application.port.`in`.command.PasswordResetCommand
import hs.kr.entrydsm.identity.application.port.out.AccountQueryPort
import hs.kr.entrydsm.identity.application.port.out.PassProofStore
import hs.kr.entrydsm.identity.application.port.out.PassVerificationProof
import hs.kr.entrydsm.identity.domain.model.Account
import hs.kr.entrydsm.identity.domain.model.StudentProfile
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

class AccountPasswordResetOwnershipVerifierTest {
    @Test
    fun rejectsMissingAccountAndOwnershipMismatch() {
        val queryPort = mock(AccountQueryPort::class.java)
        val account = mock(Account::class.java)
        val profile = mock(StudentProfile::class.java)
        `when`(queryPort.findByLoginId("known")).thenReturn(account)
        `when`(account.profile).thenReturn(profile)
        `when`(profile.name).thenReturn("홍길동")
        `when`(profile.birthdate).thenReturn(BIRTHDATE)

        val verifier = verifier(queryPort, 3, 60, MutableClock())

        assertFalse(verifier.verify(command("missing", "홍길동")))
        assertFalse(verifier.verify(command("known", "다른 이름")))
    }

    @Test
    fun blocksAttemptsAfterConfiguredLimit() {
        val queryPort = mock(AccountQueryPort::class.java)
        val account = mock(Account::class.java)
        val profile = mock(StudentProfile::class.java)
        `when`(queryPort.findByLoginId("known")).thenReturn(account)
        `when`(account.profile).thenReturn(profile)
        `when`(profile.name).thenReturn("홍길동")
        `when`(profile.birthdate).thenReturn(BIRTHDATE)
        val verifier = verifier(queryPort, 2, 60, MutableClock())

        assertTrue(verifier.verify(command("known", "홍길동")))
        assertTrue(verifier.verify(command("known", "홍길동")))
        assertFalse(verifier.verify(command("known", "홍길동")))
        verify(queryPort, org.mockito.Mockito.times(2)).findByLoginId("known")
    }

    @Test
    fun doesNotEvictActiveWindowWhenTrackingCapacityIsFull() {
        val queryPort = mock(AccountQueryPort::class.java)
        val account = mock(Account::class.java)
        val profile = mock(StudentProfile::class.java)
        `when`(queryPort.findByLoginId("known")).thenReturn(account)
        `when`(account.profile).thenReturn(profile)
        `when`(profile.name).thenReturn("홍길동")
        `when`(profile.birthdate).thenReturn(BIRTHDATE)
        val verifier = verifier(queryPort, 1, 60, MutableClock(), 1)

        assertTrue(verifier.verify(command("known", "홍길동")))
        assertFalse(verifier.verify(command("attacker", "홍길동")))
        assertFalse(verifier.verify(command("known", "홍길동")))
        verify(queryPort, never()).findByLoginId("attacker")
        verify(queryPort, org.mockito.Mockito.times(1)).findByLoginId("known")
    }

    @Test
    fun allowsRetryAfterWindowExpires() {
        val queryPort = mock(AccountQueryPort::class.java)
        val account = mock(Account::class.java)
        val profile = mock(StudentProfile::class.java)
        `when`(queryPort.findByLoginId("known")).thenReturn(account)
        `when`(account.profile).thenReturn(profile)
        `when`(profile.name).thenReturn("홍길동")
        `when`(profile.birthdate).thenReturn(BIRTHDATE)
        val clock = MutableClock()
        val verifier = verifier(queryPort, 1, 60, clock)

        assertTrue(verifier.verify(command("known", "홍길동")))
        assertFalse(verifier.verify(command("known", "홍길동")))
        clock.current = clock.current.plusSeconds(60)
        assertTrue(verifier.verify(command("known", "홍길동")))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsInvalidAttemptConfiguration() {
        verifier(mock(AccountQueryPort::class.java), 0, 60, MutableClock())
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsInvalidWindowConfiguration() {
        verifier(mock(AccountQueryPort::class.java), 3, 0, MutableClock())
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsInvalidTrackedLoginIdConfiguration() {
        verifier(mock(AccountQueryPort::class.java), 3, 60, MutableClock(), 0)
    }

    @Test
    fun rejectsBlankLoginIdWithoutQueryingAccount() {
        val queryPort = mock(AccountQueryPort::class.java)
        val verifier = verifier(queryPort, 3, 60, MutableClock())

        assertFalse(verifier.verify(command(" ", "홍길동")))
        verify(queryPort, never()).findByLoginId(" ")
    }

    private fun command(loginId: String, name: String) = PasswordResetCommand(
        loginId = loginId,
        name = name,
        birthdate = BIRTHDATE,
        newPassword = "new-password",
    )

    private fun verifier(
        queryPort: AccountQueryPort,
        maxAttempts: Int,
        windowSeconds: Long,
        clock: Clock,
        maxTrackedLoginIds: Int = 10_000,
    ): AccountPasswordResetOwnershipVerifier {
        val proofStore = mock(PassProofStore::class.java)
        `when`(proofStore.consume("known", "홍길동"))
            .thenReturn(PassVerificationProof("known", "홍길동"))
        return AccountPasswordResetOwnershipVerifier(
            queryPort,
            maxAttempts,
            windowSeconds,
            clock,
            maxTrackedLoginIds,
            proofStore,
        )
    }

    private class MutableClock : Clock() {
        var current: Instant = Instant.parse("2026-06-11T10:00:00Z")

        override fun instant(): Instant = current

        override fun getZone(): ZoneOffset = ZoneOffset.UTC

        override fun withZone(zone: java.time.ZoneId): Clock = this
    }

    private companion object {
        val BIRTHDATE: LocalDate = LocalDate.of(2009, 3, 15)
    }
}
