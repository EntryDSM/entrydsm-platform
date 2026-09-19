package hs.kr.entrydsm.application.application.port.`in`.command

data class UpdateStudyPlanCommand(
    val accountId: Long? = null,
    val studyPlan: String,
)

