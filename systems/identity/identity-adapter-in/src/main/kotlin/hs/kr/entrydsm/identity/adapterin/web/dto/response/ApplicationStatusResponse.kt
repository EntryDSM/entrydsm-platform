package hs.kr.entrydsm.identity.adapterin.web.dto.response

import hs.kr.entrydsm.identity.domain.enum.ApplicantStatus
import java.time.Instant

data class ApplicationStatusResponse(
    val applicantStatus: ApplicantStatus,
    val submittedAt: Instant?,
    val updatedAt: Instant,
)
