package hs.kr.entrydsm.application.application.port.`in`.command

data class UpdateApplicantArrivalCommand(
    val applicantId: Long,
    val isArrived: Boolean,
)
