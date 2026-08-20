package hs.kr.entrydsm.identity.adapterout.security

import hs.kr.entrydsm.identity.application.port.`in`.command.SignupCommand
import hs.kr.entrydsm.identity.application.port.out.PassProofStore
import hs.kr.entrydsm.identity.application.port.out.PassVerificationProof
import hs.kr.entrydsm.identity.domain.enum.SignupType
import java.time.LocalDate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

class AccountSignupOwnershipVerifierTest {
    @Test
    fun consumesProofBoundToPhoneAndName() {
        val store = mock(PassProofStore::class.java)
        `when`(store.consume("01012345678", "홍길동"))
            .thenReturn(PassVerificationProof("01012345678", "홍길동"))

        val result = verifier(store).verify(command("01012345678", "홍길동"))

        assertTrue(result)
        verify(store).consume("01012345678", "홍길동")
    }

    @Test
    fun rejectsProofForAnotherIdentity() {
        val store = mock(PassProofStore::class.java)
        `when`(store.consume("01012345678", "다른 이름")).thenReturn(null)

        assertFalse(verifier(store).verify(command("01012345678", "다른 이름")))
    }

    private fun verifier(store: PassProofStore) = AccountSignupOwnershipVerifier(store)

    private fun command(phone: String, name: String) = SignupCommand(
        password = "password123!",
        name = name,
        phone = phone,
        birthdate = LocalDate.of(2009, 3, 15),
        signupType = SignupType.SELF,
    )
}
