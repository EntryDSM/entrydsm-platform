package hs.kr.entrydsm.identity.adapterout.security

import hs.kr.entrydsm.identity.application.port.`in`.command.SignupCommand
import hs.kr.entrydsm.identity.application.port.out.PassProofStore
import hs.kr.entrydsm.identity.application.port.out.PassVerificationProof
import hs.kr.entrydsm.identity.domain.enum.SignupType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.time.LocalDate

class AccountSignupOwnershipVerifierTest {
    @Test
    fun consumesProofBoundToPhoneNameAndBirthdate() {
        val store = mock(PassProofStore::class.java)
        `when`(store.consume("01012345678", "홍길동", BIRTHDATE))
            .thenReturn(PassVerificationProof("01012345678", "홍길동", BIRTHDATE))

        val result = verifier(store).verify(command("01012345678", "홍길동"))

        assertTrue(result)
        verify(store).consume("01012345678", "홍길동", BIRTHDATE)
    }

    @Test
    fun rejectsProofForAnotherIdentity() {
        val store = mock(PassProofStore::class.java)
        `when`(store.consume("01012345678", "다른 이름", BIRTHDATE)).thenReturn(null)

        assertFalse(verifier(store).verify(command("01012345678", "다른 이름")))
    }

    private fun verifier(store: PassProofStore) = AccountSignupOwnershipVerifier(store)

    private fun command(phone: String, name: String) = SignupCommand(
        password = "password123!",
        name = name,
        phone = phone,
        birthdate = BIRTHDATE,
        signupType = SignupType.SELF,
    )

    private companion object {
        val BIRTHDATE: LocalDate = LocalDate.of(2009, 3, 15)
    }
}
