package hs.kr.entrydsm.application.application.port.`in`.command

data class SaveAcademicRecordCommand(
    val authorization: String? = null,
    val userId: Long? = null,
    val absentCount: Int,
    val earlyLeaveCount: Int,
    val lateCount: Int,
    val classAbsenceCount: Int,
    val volunteerTime: Int,
)
