package hs.kr.entrydsm.identity.application.service

import hs.kr.entrydsm.identity.application.port.`in`.command.SignupCommand
import hs.kr.entrydsm.identity.domain.enum.ErrorCode
import hs.kr.entrydsm.identity.domain.enum.SignupType
import hs.kr.entrydsm.identity.domain.exception.IdentityDomainException
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class IdentityServiceSupportTest {
    @Test
    fun passwordAtExactly72Utf8BytesIsAccepted() {
        requireValidPassword("가".repeat(24))
    }

    @Test(expected = IdentityDomainException::class)
    fun passwordOver72Utf8BytesIsRejected() {
        requireValidPassword("가".repeat(25))
    }

    @Test
    fun signupValidationRejectsBlankRequiredFields() {
        val exception = try {
            requireValidSignup(
                SignupCommand(
                    password = "",
                    name = "홍길동",
                    phone = "01012345678",
                    birthdate = LocalDate.of(2009, 3, 15),
                    signupType = SignupType.SELF,
                )
            )
            null
        } catch (caught: IdentityDomainException) {
            caught
        }

        assertEquals(ErrorCode.INVALID_REQUEST_BODY, exception?.errorCode)
    }
}
