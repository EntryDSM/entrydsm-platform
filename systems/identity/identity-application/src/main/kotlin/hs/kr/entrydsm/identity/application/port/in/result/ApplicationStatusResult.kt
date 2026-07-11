package hs.kr.entrydsm.identity.application.port.`in`.result

import hs.kr.entrydsm.identity.domain.ApplicantStatus
import java.time.Instant

data class ApplicationStatusResult(
    val applicantStatus: ApplicantStatus,
    val submittedAt: Instant?,
    val updatedAt: Instant,
)
