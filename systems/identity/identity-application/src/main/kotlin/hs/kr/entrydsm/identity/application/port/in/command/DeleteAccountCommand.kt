package hs.kr.entrydsm.identity.application.port.`in`.command

data class DeleteAccountCommand(
    val authorization: String?,
    val userId: Long? = null,
)
