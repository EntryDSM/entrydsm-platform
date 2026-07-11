package hs.kr.entrydsm.identity.adapterin.web.dto.response

import hs.kr.entrydsm.identity.domain.PassStatus
import java.time.Instant

data class ApplicationResultResponse(
    val passStatus: PassStatus,
    val announcedAt: Instant?,
)
