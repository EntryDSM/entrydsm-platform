package hs.kr.entrydsm.application.application.port.`in`.command

data class CalculateEvaluationCommand(
    val authorization: String? = null,
    val userId: Long? = null,
)
