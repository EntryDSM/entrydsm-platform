package hs.kr.entrydsm.identity.adapterout.security

import hs.kr.entrydsm.identity.application.port.`in`.command.SignupCommand
import hs.kr.entrydsm.identity.application.port.out.PassProofStore
import hs.kr.entrydsm.identity.application.port.out.SignupOwnershipVerifier
import org.springframework.stereotype.Component

/** Consumes the PASS proof only when the signup identity matches it. */
@Component
class AccountSignupOwnershipVerifier(
    private val passProofStore: PassProofStore,
) : SignupOwnershipVerifier {
    override fun verify(command: SignupCommand): Boolean {
        val proof = passProofStore.consume(command.phone) ?: return false
        return proof.phoneNumber == command.phone && proof.name == command.name
    }
}
