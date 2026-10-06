package hs.kr.entrydsm.identity.application.port.`in`.command

import hs.kr.entrydsm.identity.application.security.SensitiveValueMasker.REDACTED

data class LoginCommand(
    val loginId: String,
    val password: String,
    val clientIp: String = "unknown",
) {
    override fun toString(): String =
        "LoginCommand(loginId=$REDACTED, password=$REDACTED)"
}
