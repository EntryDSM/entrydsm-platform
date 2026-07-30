package hs.kr.entrydsm.application.application.port.`in`.command

data class UpdateStudyPlanCommand(
    val applicantId: Long,
    val studyPlan: String,
)

