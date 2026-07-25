package hs.kr.entrydsm.identity.application.port.`in`.command

data class LoginCommand(
    val loginId: String,
    val password: String,
) {
    override fun toString(): String = "LoginCommand(loginId=$loginId, password=[REDACTED])"
}
