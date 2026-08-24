package hs.kr.entrydsm.application.application.port.`in`.command

data class CreateApplicantCommand(
    val authorization: String? = null,
    val userId: Long? = null,
)
