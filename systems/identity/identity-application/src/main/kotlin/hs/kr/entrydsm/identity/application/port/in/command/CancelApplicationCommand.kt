package hs.kr.entrydsm.identity.application.port.`in`.command

data class CancelApplicationCommand(
    val reason: String?,
    val userId: Long? = null,
)
