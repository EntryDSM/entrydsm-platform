package hs.kr.entrydsm.application.administration.adapterout.recovery

import hs.kr.entrydsm.application.adapterout.repository.ApplicantJpaRepository
import hs.kr.entrydsm.application.application.port.`in`.ApplicationPort
import hs.kr.entrydsm.application.application.port.out.ApplicantRepository
import hs.kr.entrydsm.application.application.port.out.ApplicantStatusChanged
import hs.kr.entrydsm.application.application.port.out.ApplicantStatusEventOutbox
import hs.kr.entrydsm.application.domain.enum.AdmissionType
import hs.kr.entrydsm.application.domain.enum.GraduationType
import hs.kr.entrydsm.application.domain.model.AcademicRecord
import hs.kr.entrydsm.application.domain.nowUtc
import hs.kr.entrydsm.application.domain.service.ScoreCalculator
import org.springframework.transaction.annotation.Transactional
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

enum class EvidenceSource { ISOLATED_BACKUP, OFFICIAL_DOCUMENT }

data class ScoreRecoveryEvidence(
    val applicantId: Long,
    val accountId: Long,
    val academicRecordId: Long,
    val expectedStatusVersion: Long,
    val graduationType: GraduationType,
    val admissionType: AdmissionType,
    val source: EvidenceSource,
    val sourceReference: String,
    val verifiedBy: String,
    val artifactPath: String,
    val artifactSha256: String,
    val academicRecord: AcademicRecord,
)

/** HTTP에 노출하지 않는다. 운영 명령이 명시적으로 등록하는 서비스이다. */
open class ScoreRecoveryService(
    private val lockedApplicants: ApplicantJpaRepository,
    private val applicants: ApplicantRepository,
    private val application: ApplicationPort,
    private val outbox: ApplicantStatusEventOutbox,
) {
    @Transactional
    open fun recover(evidence: ScoreRecoveryEvidence, apply: Boolean): Long {
        require(evidence.applicantId > 0 && evidence.accountId > 0 && evidence.academicRecordId > 0)
        require(evidence.sourceReference.isNotBlank() && evidence.verifiedBy.isNotBlank())
        require(evidence.artifactSha256.matches(Regex("[a-fA-F0-9]{64}")))
        val artifact = Path.of(evidence.artifactPath)
        require(Files.isRegularFile(artifact)) { "RECOVERY_EVIDENCE_NOT_FOUND" }
        val hash = Files.newInputStream(artifact).use { stream ->
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(8192)
            while (true) {
                val count = stream.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        }
        require(hash.equals(evidence.artifactSha256, ignoreCase = true)) { "RECOVERY_EVIDENCE_CHANGED" }
        val entity = requireNotNull(lockedApplicants.findForUpdate(evidence.applicantId)) { "RECOVERY_APPLICANT_NOT_FOUND" }
        require(entity.accountId == evidence.accountId && entity.academicRecord?.id == evidence.academicRecordId &&
            entity.statusVersion == evidence.expectedStatusVersion && entity.graduationType == evidence.graduationType &&
            entity.admissionType == evidence.admissionType) { "RECOVERY_TARGET_CHANGED" }
        val original = entity.toDomain()
        // 증빙의 성적 외 원서·접수·전형 정보는 기존 원본에서 그대로 가져온다.
        val restored = original.copy(academicRecord = evidence.academicRecord)
        restored.totalScore = ScoreCalculator().calculate(restored)
        if (!apply) return original.statusVersion
        restored.statusVersion = Math.addExact(original.statusVersion, 1L)
        restored.totalScoreUpdatedAt = nowUtc()
        restored.updatedAt = restored.totalScoreUpdatedAt!!
        val saved = applicants.save(restored)
        outbox.add(ApplicantStatusChanged(
            accountId = saved.accountId, applicantId = saved.id, status = saved.status,
            occurredAt = saved.updatedAt, version = saved.statusVersion, submittedAt = saved.submittedAt,
            passStatus = saved.passStatus, passResultType = saved.passResultType, announcedAt = saved.announcedAt,
            applicationForm = requireNotNull(application.findApplicationForm(saved.accountId)),
        ))
        return saved.statusVersion
    }
}
