package hs.kr.entrydsm.application.application.port.`in`.command

data class UpdateMiddleSchoolCommand(
    val applicantId: Long,
    val authorization: String? = null,
    val userId: Long? = null,
    val schoolName: String,
    val studentNumber: String,
    val schoolPhone: String,
    val teacherName: String,
)

