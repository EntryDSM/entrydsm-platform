package hs.kr.entrydsm.identity.application.port.`in`.result

import hs.kr.entrydsm.identity.domain.enum.PassStatus
import java.time.Instant

data class ApplicationResultResult(
    val passStatus: PassStatus,
    val announcedAt: Instant?,
)
