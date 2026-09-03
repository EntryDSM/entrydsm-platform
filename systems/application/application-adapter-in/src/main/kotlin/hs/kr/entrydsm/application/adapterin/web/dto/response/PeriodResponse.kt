package hs.kr.entrydsm.application.adapterin.web.dto.response

import java.time.LocalDateTime

data class PeriodResponse(
    val startAt: LocalDateTime,
    val endAt: LocalDateTime,
)

