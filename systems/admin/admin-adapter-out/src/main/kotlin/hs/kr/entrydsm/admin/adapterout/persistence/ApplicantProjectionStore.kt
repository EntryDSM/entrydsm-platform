package hs.kr.entrydsm.admin.adapterout.persistence

import hs.kr.entrydsm.admin.adapterout.repository.ApplicantExportProjectionJpaRepository
import hs.kr.entrydsm.application.grpc.ApplicantStatus
import hs.kr.entrydsm.application.grpc.ApplicantStatusChangedEvent
import hs.kr.entrydsm.application.grpc.ApplicationFormResponse
import hs.kr.entrydsm.common.crypto.SnapshotCipher
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class ApplicantProjectionStore(
    private val repository: ApplicantExportProjectionJpaRepository,
    private val cipher: SnapshotCipher,
) {
    fun read(payload: ByteArray): ApplicationFormResponse = ApplicationFormResponse.parseFrom(cipher.decrypt(payload))
    @Transactional
    fun apply(event: ApplicantStatusChangedEvent) {
        require(event.applicantId > 0 && event.accountId > 0 && event.version > 0)
        // 이 확인 뒤의 경쟁도 applyVersion의 원자적 조건으로 차단한다.
        if ((repository.findById(event.applicantId).orElse(null)?.eventVersion ?: -1) >= event.version) return
        require(event.applicantDeleted || event.applicantStatus in ACTIVE_STATUSES + REMOVED_STATUSES)
        if (event.applicantDeleted || event.applicantStatus !in ACTIVE_STATUSES) {
            repository.applyVersion(event.applicantId, event.accountId, byteArrayOf(), event.version, true)
        } else {
            require(event.hasEncryptedApplicationForm()) { "legacy event requires reconciliation" }
            val form = read(event.encryptedApplicationForm.toByteArray())
            require(form.applicantId == event.applicantId && form.userId == event.accountId && form.statusVersion == event.version && form.applicantStatus == event.applicantStatus)
            save(form)
        }
    }

    @Transactional
    fun save(form: ApplicationFormResponse) {
        require(form.applicantId > 0 && form.userId > 0 && form.statusVersion >= 0)
        require(form.applicantStatus in ACTIVE_STATUSES + REMOVED_STATUSES)
        val deleted = form.applicantStatus !in ACTIVE_STATUSES
        repository.applyVersion(form.applicantId, form.userId, if (deleted) byteArrayOf() else cipher.encrypt(form.toByteArray()), form.statusVersion, deleted)
    }

    @Transactional
    fun removeIfUnchanged(id: Long, version: Long) {
        repository.removeIfUnchanged(id, version)
    }

    companion object {
        private val REMOVED_STATUSES = setOf(ApplicantStatus.APPLICANT_STATUS_NONE, ApplicantStatus.APPLICANT_STATUS_DRAFT, ApplicantStatus.APPLICANT_STATUS_CANCELED)
        val ACTIVE_STATUSES = setOf(ApplicantStatus.APPLICANT_STATUS_SUBMITTED, ApplicantStatus.APPLICANT_STATUS_ARRIVAL,
            ApplicantStatus.APPLICANT_STATUS_REVIEWING, ApplicantStatus.APPLICANT_STATUS_COMPLETED)
    }
}
