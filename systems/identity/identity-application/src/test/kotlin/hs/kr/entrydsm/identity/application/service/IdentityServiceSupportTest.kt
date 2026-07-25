package hs.kr.entrydsm.identity.application.service

import hs.kr.entrydsm.identity.application.port.`in`.command.SignupCommand
import hs.kr.entrydsm.identity.domain.enum.SignupType
import hs.kr.entrydsm.identity.domain.exception.IdentityDomainException
import java.time.LocalDate
import org.junit.Test

class IdentityServiceSupportTest {
    @Test(expected = IdentityDomainException::class)
    fun signupValidationRejectsBlankRequiredFields() {
        requireValidSignup(
            SignupCommand(
                password = "",
                name = "홍길동",
                phone = "01012345678",
                birthdate = LocalDate.of(2009, 3, 15),
                signupType = SignupType.SELF,
            )
        )
    }
}
