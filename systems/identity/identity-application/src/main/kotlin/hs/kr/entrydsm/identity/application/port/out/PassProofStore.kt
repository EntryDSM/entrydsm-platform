package hs.kr.entrydsm.identity.application.port.out

interface PassProofStore {
    /** Atomically claims the provider token and stores its proof. */
    fun saveForToken(token: String, phoneNumber: String, name: String, ttlSeconds: Long): Boolean

    /** Atomically returns and removes a proof only when phone number and name match. */
    fun consume(phoneNumber: String, name: String): PassVerificationProof?
}

data class PassVerificationProof(
    val phoneNumber: String,
    val name: String,
)

class PassProofStoreUnavailableException(cause: Throwable) : RuntimeException(cause)
