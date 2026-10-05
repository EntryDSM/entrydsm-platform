package hs.kr.entrydsm.application.application.port.`in`.result

import java.time.LocalDateTime

data class LandingResult(
    val applicantName: String?,
    val applicationStartAt: LocalDateTime? = null,
    val applicationEndAt: LocalDateTime? = null,
    val resultAnnouncedAt: LocalDateTime? = null,
)
