package hs.kr.entrydsm.application.application.port.`in`.command

data class CalculateEvaluationCommand(
    val applicantId: Long,
    val authorization: String? = null,
    val userId: Long? = null,
)
