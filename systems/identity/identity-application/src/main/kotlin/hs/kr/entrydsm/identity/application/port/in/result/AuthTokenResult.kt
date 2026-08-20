package hs.kr.entrydsm.identity.application.port.`in`.result

import hs.kr.entrydsm.identity.application.security.jwt.JwtToken
import hs.kr.entrydsm.identity.application.security.SensitiveValueMasker.REDACTED
import hs.kr.entrydsm.identity.domain.enum.AccountStatus
import hs.kr.entrydsm.identity.domain.enum.Role

data class AuthTokenResult(
    val userId: Long,
    val role: Role,
    val status: AccountStatus,
    val accessToken: JwtToken,
    val refreshToken: JwtToken,
) {
    override fun toString(): String =
        "AuthTokenResult(userId=$userId, role=$role, status=$status, accessToken=$REDACTED, refreshToken=$REDACTED)"
}
