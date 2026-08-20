package hs.kr.entrydsm.identity.application.port.out

import hs.kr.entrydsm.identity.application.port.`in`.command.PasswordResetCommand

/**
 * Verifies that a password-reset request is backed by an external ownership proof.
 *
 * Implementations must enforce proof expiry, single-use semantics, and attempt limits.
 */
fun interface PasswordResetOwnershipVerifier {
    fun verify(command: PasswordResetCommand): Boolean
}
