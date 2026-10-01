package hs.kr.entrydsm.admin.adapterout.repository

import hs.kr.entrydsm.admin.adapterout.entity.AdmissionQuotaJpaEntity
import hs.kr.entrydsm.admin.adapterout.entity.ApplicantExportEventJpaEntity
import hs.kr.entrydsm.admin.adapterout.entity.ApplicantExportProjectionJpaEntity
import hs.kr.entrydsm.admin.adapterout.entity.ExportJobJpaEntity
import hs.kr.entrydsm.admin.adapterout.entity.ScorePolicyJpaEntity
import hs.kr.entrydsm.admin.adapterout.entity.ScreeningJpaEntity
import hs.kr.entrydsm.admin.domain.enum.ApplicantStatus
import hs.kr.entrydsm.admin.domain.enum.ExportStatus
import hs.kr.entrydsm.admin.domain.enum.ExportType
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface ScreeningJpaRepository : JpaRepository<ScreeningJpaEntity, Long> {
    fun existsByStatus(status: ApplicantStatus): Boolean
}

interface ApplicantExportEventJpaRepository : JpaRepository<ApplicantExportEventJpaEntity, String> {
    fun findAllByProcessedFalse(): List<ApplicantExportEventJpaEntity>
    fun findTopByApplicantIdOrderByEventVersionDesc(applicantId: Long): ApplicantExportEventJpaEntity?
}

interface ApplicantExportProjectionJpaRepository : JpaRepository<ApplicantExportProjectionJpaEntity, Long> {
    fun findAllByDeletedFalse(): List<ApplicantExportProjectionJpaEntity>
    fun findAllByApplicantIdInAndDeletedFalse(applicantIds: Collection<Long>): List<ApplicantExportProjectionJpaEntity>

    // 버전을 마지막에 갱신해야 MySQL의 좌→우 대입에서도 각 조건이 이전 버전을 비교한다.
    @org.springframework.data.jpa.repository.Modifying
    @Query(value = """
        INSERT INTO applicant_export_projection (applicant_id, account_id, payload, event_version, deleted)
        VALUES (:id, :accountId, :payload, :version, :deleted)
        ON DUPLICATE KEY UPDATE
          account_id = CASE WHEN event_version < VALUES(event_version) THEN VALUES(account_id) ELSE account_id END,
          payload = CASE WHEN event_version < VALUES(event_version) THEN VALUES(payload) ELSE payload END,
          deleted = CASE WHEN event_version < VALUES(event_version) THEN VALUES(deleted) ELSE deleted END,
          event_version = GREATEST(event_version, VALUES(event_version))
    """, nativeQuery = true)
    fun applyVersion(@Param("id") id: Long, @Param("accountId") accountId: Long,
        @Param("payload") payload: ByteArray, @Param("version") version: Long, @Param("deleted") deleted: Boolean): Int

    @org.springframework.data.jpa.repository.Modifying
    @Query(value = """
        UPDATE applicant_export_projection SET payload = '', deleted = TRUE
        WHERE applicant_id = :id AND event_version = :version
    """, nativeQuery = true)
    fun removeIfUnchanged(@Param("id") id: Long, @Param("version") version: Long): Int
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
