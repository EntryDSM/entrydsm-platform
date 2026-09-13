package hs.kr.entrydsm.application.application.port.`in`.result

import hs.kr.entrydsm.application.domain.enum.ApplicantStatus
import hs.kr.entrydsm.application.domain.enum.PassResultStatus
import java.time.LocalDateTime

data class ApplicationSnapshotResult(
    val userId: Long,
    val applicantStatus: ApplicantStatus,
    val submittedAt: LocalDateTime?,
    val updatedAt: LocalDateTime,
    val passStatus: PassResultStatus,
    val announcedAt: LocalDateTime?,
)
