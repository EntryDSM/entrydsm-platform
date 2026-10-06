package hs.kr.entrydsm.admin.domain.port.out

import java.time.Instant

data class ApplicationCorrectionAudit(
    val applicantId: Long,
    val editorId: String,
    val reason: String,
    val changedFields: Set<String>,
    val version: Long,
    val occurredAt: Instant,
)

fun interface ApplicationCorrectionAuditRepository {
    fun add(audit: ApplicationCorrectionAudit)
}
