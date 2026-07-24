package hs.kr.entrydsm.identity.application.port.`in`.command

data class CancelApplicationCommand(
    val authorization: String?,
    val reason: String?,
)
