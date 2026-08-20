package hs.kr.entrydsm.identity.application.port.out

import hs.kr.entrydsm.identity.application.port.`in`.command.SignupCommand

/** Verifies that signup data is backed by a single-use external ownership proof. */
fun interface SignupOwnershipVerifier {
    fun verify(command: SignupCommand): Boolean
}
