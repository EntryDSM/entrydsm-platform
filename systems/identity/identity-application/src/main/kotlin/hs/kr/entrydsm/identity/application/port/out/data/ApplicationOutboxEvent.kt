package hs.kr.entrydsm.identity.application.port.out.data

import hs.kr.entrydsm.identity.domain.enum.ApplicantStatus
import hs.kr.entrydsm.identity.domain.enum.PassStatus
import java.time.Instant

data class ApplicationOutboxEvent(
    val eventId: String,
    val userId: Long,
    val version: Long,
    val applicantStatus: ApplicantStatus,
    val submittedAt: Instant?,
    val passStatus: PassStatus,
    val announcedAt: Instant?,
    val occurredAt: Instant,
    val attempts: Int,
    val lastError: String?,
)
