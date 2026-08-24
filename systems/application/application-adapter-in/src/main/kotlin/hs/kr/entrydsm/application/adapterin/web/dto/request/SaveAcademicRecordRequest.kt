package hs.kr.entrydsm.application.adapterin.web.dto.request

data class SaveAcademicRecordRequest(
    val absentCount: Int,
    val earlyLeaveCount: Int,
    val lateCount: Int,
    val classAbsenceCount: Int,
    val volunteerTime: Int,
)
