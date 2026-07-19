package hs.kr.entrydsm.identity.application.port.out.data

import hs.kr.entrydsm.identity.domain.ApplicantStatus
import hs.kr.entrydsm.identity.domain.PassStatus
import java.time.Instant

data class ApplicationSnapshot(
    val userId: String,
    val applicantStatus: ApplicantStatus,
    val submittedAt: Instant?,
    val updatedAt: Instant,
    val passStatus: PassStatus,
    val announcedAt: Instant?,
)
