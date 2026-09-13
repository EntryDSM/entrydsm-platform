package hs.kr.entrydsm.identity.adapterin.web.dto.response

import java.time.Instant

data class ApplicationResultResponse(
    val passStatus: String,
    val announcedAt: Instant?,
)
