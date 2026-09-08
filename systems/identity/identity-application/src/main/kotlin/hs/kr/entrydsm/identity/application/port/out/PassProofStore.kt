package hs.kr.entrydsm.identity.application.port.out

import java.time.LocalDate

interface PassProofStore {
    /** Atomically claims the provider token and stores its proof. */
    fun saveForToken(
        token: String,
        phoneNumber: String,
        name: String,
        birthdate: LocalDate,
        ttlSeconds: Long,
    ): Boolean

    /** Atomically returns and removes a proof only when all identity fields match. */
    fun consume(phoneNumber: String, name: String, birthdate: LocalDate): PassVerificationProof?
}

data class PassVerificationProof(
    val phoneNumber: String,
    val name: String,
    val birthdate: LocalDate,
)

class PassProofStoreUnavailableException(cause: Throwable) : RuntimeException(cause)
