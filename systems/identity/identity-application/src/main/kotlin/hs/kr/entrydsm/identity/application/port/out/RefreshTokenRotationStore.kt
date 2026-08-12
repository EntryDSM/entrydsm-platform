package hs.kr.entrydsm.identity.application.port.out

import java.time.Instant

/** Records refresh token identifiers that have already been exchanged. */
fun interface RefreshTokenRotationStore {
    /** Returns true only when this call consumed the identifier for the first time. */
    fun consume(tokenId: String, expiresAt: Instant): Boolean
}
