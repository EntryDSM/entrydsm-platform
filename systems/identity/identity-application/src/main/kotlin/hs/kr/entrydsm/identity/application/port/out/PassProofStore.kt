package hs.kr.entrydsm.identity.application.port.out

interface PassProofStore {
    fun save(phoneNumber: String, name: String, ttlSeconds: Long)

    /** Atomically returns and removes the proof for the supplied phone number. */
    fun consume(phoneNumber: String): PassVerificationProof?
}

data class PassVerificationProof(
    val phoneNumber: String,
    val name: String,
)

class PassProofStoreUnavailableException(cause: Throwable) : RuntimeException(cause)
