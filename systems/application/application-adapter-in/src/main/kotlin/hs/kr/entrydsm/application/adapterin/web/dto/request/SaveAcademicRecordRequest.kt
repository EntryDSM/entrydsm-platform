package hs.kr.entrydsm.application.adapterin.web.dto.request

import jakarta.validation.constraints.PositiveOrZero

data class SaveAcademicRecordRequest(
    @field:PositiveOrZero(message = "미인정 결석 횟수는 0 이상이어야 합니다")
    val absentCount: Int,
    @field:PositiveOrZero(message = "미인정 조퇴 횟수는 0 이상이어야 합니다")
    val earlyLeaveCount: Int,
    @field:PositiveOrZero(message = "미인정 지각 횟수는 0 이상이어야 합니다")
    val lateCount: Int,
    @field:PositiveOrZero(message = "미인정 결과 횟수는 0 이상이어야 합니다")
    val classAbsenceCount: Int,
    @field:PositiveOrZero(message = "봉사활동 시간은 0 이상이어야 합니다")
    val volunteerTime: Int,
)
