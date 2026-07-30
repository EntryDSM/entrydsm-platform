package hs.kr.entrydsm.application.application.port.`in`.command

data class UpdateIntroductionCommand(
    val applicantId: Long,
    val introduction: String,
)

