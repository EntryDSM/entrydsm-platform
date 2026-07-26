package hs.kr.entrydsm.identity.application.port.out

import java.time.Instant

/** Records refresh token identifiers that have already been exchanged. */
fun interface RefreshTokenRotationStore {
    fun consume(tokenId: String, expiresAt: Instant): Boolean
}
