package hs.kr.entrydsm.application.adapterin.web.dto.response

import java.time.LocalDateTime

data class ScheduleResponse(
    val applicationPeriod: PeriodResponse,
    val resultAnnouncedAt: LocalDateTime,
)

