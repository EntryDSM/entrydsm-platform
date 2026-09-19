package hs.kr.entrydsm.application.application.port.`in`.command

data class UpdateIntroductionCommand(
    val userId: Long? = null,
    val introduction: String,
)

