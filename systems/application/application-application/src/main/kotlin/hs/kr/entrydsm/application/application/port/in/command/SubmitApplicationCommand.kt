package hs.kr.entrydsm.application.application.port.`in`.command

data class SubmitApplicationCommand(
    val authorization: String? = null,
    val userId: Long? = null,
)

