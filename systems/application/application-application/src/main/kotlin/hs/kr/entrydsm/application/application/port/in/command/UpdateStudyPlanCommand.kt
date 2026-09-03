package hs.kr.entrydsm.application.application.port.`in`.command

data class UpdateStudyPlanCommand(
    val applicantId: Long,
    val userId: Long? = null,
    val studyPlan: String,
)

