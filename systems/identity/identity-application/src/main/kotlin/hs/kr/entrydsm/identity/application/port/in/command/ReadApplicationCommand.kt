package hs.kr.entrydsm.identity.application.port.`in`.command

data class ReadApplicationCommand(
    val authorization: String?,
    val userId: Long? = null,
)
