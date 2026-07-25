package hs.kr.entrydsm.identity.application.port.`in`.command

data class RefreshTokenCommand(
    val refreshToken: String?,
) {
    override fun toString(): String = "RefreshTokenCommand(refreshToken=[REDACTED])"
}
