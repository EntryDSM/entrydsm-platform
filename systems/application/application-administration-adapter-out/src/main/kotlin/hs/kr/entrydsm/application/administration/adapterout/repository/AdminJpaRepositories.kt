package hs.kr.entrydsm.application.administration.adapterout.repository

import hs.kr.entrydsm.application.administration.adapterout.entity.AdmissionQuotaJpaEntity
import hs.kr.entrydsm.application.administration.adapterout.entity.ExportJobJpaEntity
import hs.kr.entrydsm.application.administration.adapterout.entity.ScorePolicyJpaEntity
import hs.kr.entrydsm.application.administration.adapterout.entity.ScreeningJpaEntity
import hs.kr.entrydsm.admin.domain.enum.ApplicantStatus
import hs.kr.entrydsm.admin.domain.enum.ExportStatus
import hs.kr.entrydsm.admin.domain.enum.ExportType
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface ScreeningJpaRepository : JpaRepository<ScreeningJpaEntity, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from ScreeningJpaEntity s where s.applicantId = :applicantId")
    fun findForUpdate(@Param("applicantId") applicantId: Long): ScreeningJpaEntity?

    fun existsByStatus(status: ApplicantStatus): Boolean
}

interface ScorePolicyJpaRepository : JpaRepository<ScorePolicyJpaEntity, Long> {
    fun findTopByOrderByPolicyVersionDesc(): ScorePolicyJpaEntity?
}

interface AdmissionQuotaJpaRepository : JpaRepository<AdmissionQuotaJpaEntity, Long>

interface ExportJobJpaRepository : JpaRepository<ExportJobJpaEntity, Long> {
    fun findByExportJobId(exportJobId: String): ExportJobJpaEntity?

    @Query(
        value = """
            SELECT * FROM export_job
            WHERE status = 'PENDING'
               OR (status = 'PROCESSING' AND started_at < :staleBefore)
            ORDER BY created_at
            LIMIT 1
            FOR UPDATE SKIP LOCKED
        """,
        nativeQuery = true,
    )
    fun findClaimable(@Param("staleBefore") staleBefore: java.time.Instant): ExportJobJpaEntity?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    fun findAllByTypeAndStatusAndObjectKeyIsNotNull(
        type: ExportType,
        status: ExportStatus,
    ): List<ExportJobJpaEntity>
}
