package hs.kr.entrydsm.application.application.port.`in`.command

data class UpdateIntroductionCommand(
    val applicantId: Long,
    val authorization: String? = null,
    val userId: Long? = null,
    val introduction: String,
)

