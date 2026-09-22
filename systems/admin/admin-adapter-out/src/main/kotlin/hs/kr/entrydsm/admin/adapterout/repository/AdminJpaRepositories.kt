package hs.kr.entrydsm.admin.adapterout.repository

import hs.kr.entrydsm.admin.adapterout.entity.AdmissionQuotaJpaEntity
import hs.kr.entrydsm.admin.adapterout.entity.ApplicantExportEventJpaEntity
import hs.kr.entrydsm.admin.adapterout.entity.ApplicantExportProjectionJpaEntity
import hs.kr.entrydsm.admin.adapterout.entity.ExportJobJpaEntity
import hs.kr.entrydsm.admin.adapterout.entity.ScorePolicyJpaEntity
import hs.kr.entrydsm.admin.adapterout.entity.ScreeningJpaEntity
import hs.kr.entrydsm.admin.domain.enum.ExportStatus
import hs.kr.entrydsm.admin.domain.enum.ExportType
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock

interface ScreeningJpaRepository : JpaRepository<ScreeningJpaEntity, Long>

interface ApplicantExportEventJpaRepository : JpaRepository<ApplicantExportEventJpaEntity, String> {
    fun findAllByProcessedFalse(): List<ApplicantExportEventJpaEntity>
}

interface ApplicantExportProjectionJpaRepository : JpaRepository<ApplicantExportProjectionJpaEntity, Long>

interface ScorePolicyJpaRepository : JpaRepository<ScorePolicyJpaEntity, Long> {
    fun findTopByOrderByPolicyVersionDesc(): ScorePolicyJpaEntity?
}

interface AdmissionQuotaJpaRepository : JpaRepository<AdmissionQuotaJpaEntity, Long>

interface ExportJobJpaRepository : JpaRepository<ExportJobJpaEntity, Long> {
    fun findByExportJobId(exportJobId: String): ExportJobJpaEntity?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    fun findAllByTypeAndStatusAndObjectKeyIsNotNull(
        type: ExportType,
        status: ExportStatus,
    ): List<ExportJobJpaEntity>
}
