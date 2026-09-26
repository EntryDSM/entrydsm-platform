package hs.kr.entrydsm.application.application.port.out

import hs.kr.entrydsm.application.domain.enum.ApplicantStatus
import hs.kr.entrydsm.application.domain.enum.PassResultStatus
import hs.kr.entrydsm.application.domain.enum.ResultType
import java.time.LocalDateTime
import java.util.UUID

data class ApplicantStatusChanged(
    val eventId: UUID = UUID.randomUUID(),
    val accountId: Long,
    val applicantId: Long,
    val status: ApplicantStatus,
    val occurredAt: LocalDateTime,
    val version: Long,
    val submittedAt: LocalDateTime?,
    val passStatus: PassResultStatus,
    val announcedAt: LocalDateTime?,
    val passResultType: ResultType? = null,
    val deleted: Boolean = false,
)

fun interface ApplicantStatusEventOutbox {
    fun add(event: ApplicantStatusChanged)
}
