package hs.kr.entrydsm.identity.adapterout.security

import hs.kr.entrydsm.identity.application.port.`in`.command.PasswordResetCommand
import hs.kr.entrydsm.identity.application.port.out.AccountQueryPort
import hs.kr.entrydsm.identity.application.port.out.PassProofStore
import hs.kr.entrydsm.identity.application.port.out.PassVerificationProof
import hs.kr.entrydsm.identity.domain.model.Account
import hs.kr.entrydsm.identity.domain.model.StudentProfile
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.time.LocalDate

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

        val verifier = verifier(queryPort)

        assertFalse(verifier.verify(command("missing", "홍길동")))
        assertFalse(verifier.verify(command("known", "다른 이름")))
        assertTrue(verifier.verify(command("known", "홍길동")))
    }

    @Test
    fun rejectsBlankLoginIdWithoutQueryingAccount() {
        val queryPort = mock(AccountQueryPort::class.java)
        val verifier = verifier(queryPort)

        assertFalse(verifier.verify(command(" ", "홍길동")))
        verify(queryPort, never()).findByLoginId(" ")
    }

    @Test
    fun rejectsMatchingProfileWhenPassProofIsMissing() {
        val queryPort = mock(AccountQueryPort::class.java)
        val account = mock(Account::class.java)
        val profile = mock(StudentProfile::class.java)
        `when`(queryPort.findByLoginId("known")).thenReturn(account)
        `when`(account.profile).thenReturn(profile)
        `when`(profile.name).thenReturn("홍길동")
        `when`(profile.birthdate).thenReturn(BIRTHDATE)

        val verifier = verifier(queryPort, proof = null)

        assertFalse(verifier.verify(command("known", "홍길동")))
    }

    private fun command(loginId: String, name: String) = PasswordResetCommand(
        loginId = loginId,
        name = name,
        birthdate = BIRTHDATE,
        newPassword = "new-password",
    )

    private fun verifier(
        queryPort: AccountQueryPort,
        proof: PassVerificationProof? = PassVerificationProof("known", "홍길동", BIRTHDATE),
    ): AccountPasswordResetOwnershipVerifier {
        val proofStore = mock(PassProofStore::class.java)
        `when`(proofStore.consume("known", "홍길동", BIRTHDATE))
            .thenReturn(proof)
        return AccountPasswordResetOwnershipVerifier(queryPort, proofStore)
    }

    private companion object {
        val BIRTHDATE: LocalDate = LocalDate.of(2009, 3, 15)
    }
}
