package hs.kr.entrydsm.application.adapterin.web.dto.request

data class SaveAcademicRecordRequest(
    val applicantId: Long,
    val absentCount: Int,
    val earlyLeaveCount: Int,
    val lateCount: Int,
    val classAbsenceCount: Int,
    val volunteerTime: Int? = null,
    val volunteer_time: Int? = null,
) {
    fun resolvedVolunteerTime(): Int =
        volunteerTime ?: volunteer_time
            ?: throw IllegalArgumentException("volunteerTime is required")
}
