package hs.kr.entrydsm.identity.application.port.out

/** Stores the current refresh-token version for each user. */
interface RefreshTokenRevocationStore {
    fun currentVersion(userId: Long): Long

    fun revokeAll(userId: Long)
}
