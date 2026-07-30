package hs.kr.entrydsm.application.application.port.`in`.command

data class SaveAcademicRecordCommand(
    val applicantId: Long,
    val absentCount: Int,
    val earlyLeaveCount: Int,
    val lateCount: Int,
    val classAbsenceCount: Int,
    val volunteerTime: Int,
)
