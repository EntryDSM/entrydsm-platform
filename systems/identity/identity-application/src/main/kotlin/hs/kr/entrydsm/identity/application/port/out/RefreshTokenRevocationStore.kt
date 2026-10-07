package hs.kr.entrydsm.identity.application.port.out

/** 계정의 DB 토큰 버전을 증가시켜 기존 액세스·리프레시 토큰을 폐기한다. */
interface RefreshTokenRevocationStore {
    fun revokeAll(userId: Long)
}
