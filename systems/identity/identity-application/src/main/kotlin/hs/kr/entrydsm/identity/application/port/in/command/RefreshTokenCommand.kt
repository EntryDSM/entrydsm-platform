package hs.kr.entrydsm.identity.application.port.`in`.command

import hs.kr.entrydsm.identity.application.security.SensitiveValueMasker.REDACTED

data class RefreshTokenCommand(
    val refreshToken: String?,
) {
    override fun toString(): String = "RefreshTokenCommand(refreshToken=$REDACTED)"
}
