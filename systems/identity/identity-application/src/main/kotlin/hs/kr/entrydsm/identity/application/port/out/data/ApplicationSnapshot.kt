package hs.kr.entrydsm.identity.application.port.out.data

import hs.kr.entrydsm.identity.domain.enum.ApplicantStatus
import hs.kr.entrydsm.identity.domain.enum.PassStatus
import java.time.Instant

data class ApplicationSnapshot(
    val userId: Long,
    val applicantStatus: ApplicantStatus,
    val submittedAt: Instant?,
    val updatedAt: Instant,
    val passStatus: PassStatus,
    val announcedAt: Instant?,
)
