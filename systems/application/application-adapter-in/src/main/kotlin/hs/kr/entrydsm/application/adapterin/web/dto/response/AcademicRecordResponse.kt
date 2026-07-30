package hs.kr.entrydsm.application.adapterin.web.dto.response

data class AcademicRecordResponse(
    val absentCount: Int,
    val earlyLeaveCount: Int,
    val lateCount: Int,
    val classAbsenceCount: Int,
    val volunteerTime: Int,
)

