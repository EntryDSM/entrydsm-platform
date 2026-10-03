package hs.kr.entrydsm.application.adapterin.web.dto.request

import jakarta.validation.constraints.Positive

data class SaveAcademicRecordRequest(
    @field:Positive(message = "미인정 결석 횟수는 0보다 커야 합니다")
    val absentCount: Int,
    @field:Positive(message = "미인정 조퇴 횟수는 0보다 커야 합니다")
    val earlyLeaveCount: Int,
    @field:Positive(message = "미인정 지각 횟수는 0보다 커야 합니다")
    val lateCount: Int,
    @field:Positive(message = "미인정 결과 횟수는 0보다 커야 합니다")
    val classAbsenceCount: Int,
    @field:Positive(message = "봉사활동 시간은 0보다 커야 합니다")
    val volunteerTime: Int,
)
