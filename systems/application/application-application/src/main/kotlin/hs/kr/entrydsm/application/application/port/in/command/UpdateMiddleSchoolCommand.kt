package hs.kr.entrydsm.application.application.port.`in`.command

data class UpdateMiddleSchoolCommand(
    val accountId: Long? = null,
    val schoolCode: String,
    val schoolName: String,
    val studentNumber: String,
    val schoolPhone: String,
    val teacherName: String,
)

