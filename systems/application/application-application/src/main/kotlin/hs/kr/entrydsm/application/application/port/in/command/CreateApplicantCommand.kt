package hs.kr.entrydsm.application.application.port.`in`.command

data class CreateApplicantCommand(
    val accountId: Long,
    val authorization: String? = null,
    val userId: Long? = null,
)
