package hs.kr.entrydsm.identity.adapterout.security

import hs.kr.entrydsm.identity.application.port.`in`.command.PasswordResetCommand
import hs.kr.entrydsm.identity.application.port.out.AccountQueryPort
import hs.kr.entrydsm.identity.application.port.out.PassProofStore
import hs.kr.entrydsm.identity.application.port.out.PasswordResetOwnershipVerifier
import org.springframework.stereotype.Component

/** PASS 증빙과 계정 소유자 정보를 검증한다. */
@Component
class AccountPasswordResetOwnershipVerifier(
    private val accountQueryPort: AccountQueryPort,
    private val passProofStore: PassProofStore,
) : PasswordResetOwnershipVerifier {
    override fun verify(command: PasswordResetCommand): Boolean {
        if (command.loginId.isBlank()) return false
        val account = accountQueryPort.findByLoginId(command.loginId) ?: return false
        if (account.profile.name != command.name || account.profile.birthdate != command.birthdate) return false
        return passProofStore.consume(command.loginId, command.name, command.birthdate) != null
    }
}
