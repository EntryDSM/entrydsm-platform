package hs.kr.entrydsm.identity.application.port.`in`.result

import hs.kr.entrydsm.identity.application.security.jwt.JwtToken
import hs.kr.entrydsm.identity.domain.enum.AccountStatus

data class AuthTokenResult(
    val userId: Long,
    val role: String,
    val status: AccountStatus,
    val accessToken: JwtToken,
    val refreshToken: JwtToken,
) {
    override fun toString(): String =
        "AuthTokenResult(userId=$userId, role=$role, status=$status, accessToken=[REDACTED], refreshToken=[REDACTED])"
}
