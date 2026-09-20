package hs.kr.entrydsm.application.application.port.`in`.command

data class UpdateIntroductionCommand(
    val accountId: Long? = null,
    val introduction: String,
)

