package hs.kr.entrydsm.application.adapterin.web.config

import java.time.LocalDateTime
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class LandingScheduleProperties(
    @Value("\${entrydsm.application.schedule.application-start-at}")
    val applicationStartAt: LocalDateTime,

    @Value("\${entrydsm.application.schedule.application-end-at}")
    val applicationEndAt: LocalDateTime,

    @Value("\${entrydsm.application.schedule.result-announced-at}")
    val resultAnnouncedAt: LocalDateTime,
)
