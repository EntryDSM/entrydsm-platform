package hs.kr.entrydsm.notification.domain.model

import java.time.LocalDate
import java.time.LocalDateTime

data class RecruitmentGuideline(
    val id: Long,
    val title: String,
    val description: String,
    val schedule: RecruitmentSchedule,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
)

data class RecruitmentSchedule(
    val applicationStart: LocalDate,
    val applicationEnd: LocalDate,
    val resultAt: LocalDate,
)
