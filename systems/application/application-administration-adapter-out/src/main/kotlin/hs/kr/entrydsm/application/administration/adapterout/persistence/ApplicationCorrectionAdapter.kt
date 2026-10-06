package hs.kr.entrydsm.application.administration.adapterout.persistence

import hs.kr.entrydsm.admin.domain.enum.ApplicantStatus
import hs.kr.entrydsm.admin.domain.port.out.*
import hs.kr.entrydsm.application.adapterout.entity.PersonalDataConverter
import hs.kr.entrydsm.application.administration.adapterout.repository.ScreeningJpaRepository
import jakarta.persistence.*
import java.time.Instant
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Component

/** 사유는 개인정보 암호화 키로 암호화하며 변경 전후 개인정보 값은 저장하지 않는다. */
@Entity
@Table(name = "application_correction_audit")
class ApplicationCorrectionAuditJpaEntity(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) val id: Long? = null,
    @Column(name = "applicant_id", nullable = false) val applicantId: Long = 0,
    @Column(name = "editor_id", nullable = false, length = 128) val editorId: String = "",
    @Convert(converter = PersonalDataConverter::class)
    @Column(nullable = false, length = 4096) val reason: String = "",
    @Column(name = "changed_fields", nullable = false, length = 1024) val changedFields: String = "",
    @Column(nullable = false) val version: Long = 0,
    @Column(name = "occurred_at", nullable = false) val occurredAt: Instant = Instant.EPOCH,
)

interface ApplicationCorrectionAuditJpaRepository : JpaRepository<ApplicationCorrectionAuditJpaEntity, Long>

@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = ["application.administration.enabled"], havingValue = "true")
@Component
class ApplicationCorrectionAdapter(
    private val screening: ScreeningJpaRepository,
    private val audits: ApplicationCorrectionAuditJpaRepository,
) : ApplicationCorrectionStatePort, ApplicationCorrectionAuditRepository {
    override fun lock(applicantId: Long): ApplicationCorrectionState {
        val state = screening.findForUpdate(applicantId)
        return ApplicationCorrectionState(state != null && state.status != ApplicantStatus.PENDING)
    }

    override fun add(audit: ApplicationCorrectionAudit) {
        require(audit.editorId.length <= 128)
        audits.saveAndFlush(ApplicationCorrectionAuditJpaEntity(
            applicantId = audit.applicantId, editorId = audit.editorId, reason = audit.reason,
            changedFields = audit.changedFields.sorted().joinToString(","), version = audit.version, occurredAt = audit.occurredAt,
        ))
    }
}
