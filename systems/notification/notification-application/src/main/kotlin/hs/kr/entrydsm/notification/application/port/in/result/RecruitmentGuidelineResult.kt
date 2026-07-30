package hs.kr.entrydsm.notification.application.port.`in`.result

import java.time.LocalDate
import java.time.LocalDateTime

data class RecruitmentGuidelineResult(
    val recruitmentId: Long,
    val title: String,
    val description: String,
    val schedule: RecruitmentScheduleResult,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
)

data class RecruitmentScheduleResult(
    val applicationStart: LocalDate,
    val applicationEnd: LocalDate,
    val resultAt: LocalDate,
)
