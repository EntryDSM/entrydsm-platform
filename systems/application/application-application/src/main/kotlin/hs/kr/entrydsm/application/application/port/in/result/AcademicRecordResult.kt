package hs.kr.entrydsm.application.application.port.`in`.result

data class AcademicRecordResult(
    val absentCount: Int,
    val earlyLeaveCount: Int,
    val lateCount: Int,
    val classAbsenceCount: Int,
    val volunteerTime: Int,
)

