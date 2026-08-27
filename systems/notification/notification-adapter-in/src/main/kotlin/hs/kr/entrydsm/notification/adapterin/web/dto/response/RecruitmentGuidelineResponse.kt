package hs.kr.entrydsm.notification.adapterin.web.dto.response

import java.time.LocalDate
import java.time.LocalDateTime

data class RecruitmentGuidelineResponse(
    val recruitmentId: Long,
    val title: String,
    val description: String,
    val schedule: RecruitmentScheduleResponse,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
)

data class RecruitmentScheduleResponse(
    val applicationStart: LocalDate,
    val applicationEnd: LocalDate,
    val resultAt: LocalDate,
)
