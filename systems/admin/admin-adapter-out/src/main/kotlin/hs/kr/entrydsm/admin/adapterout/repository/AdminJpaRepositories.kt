package hs.kr.entrydsm.admin.adapterout.repository

import hs.kr.entrydsm.admin.adapterout.entity.AdmissionQuotaJpaEntity
import hs.kr.entrydsm.admin.adapterout.entity.ExportJobJpaEntity
import hs.kr.entrydsm.admin.adapterout.entity.ScorePolicyJpaEntity
import hs.kr.entrydsm.admin.adapterout.entity.ScreeningJpaEntity
import org.springframework.data.jpa.repository.JpaRepository

interface ScreeningJpaRepository : JpaRepository<ScreeningJpaEntity, Long>

interface ScorePolicyJpaRepository : JpaRepository<ScorePolicyJpaEntity, Long> {
    fun findTopByOrderByPolicyVersionDesc(): ScorePolicyJpaEntity?
}

interface AdmissionQuotaJpaRepository : JpaRepository<AdmissionQuotaJpaEntity, Long>

interface ExportJobJpaRepository : JpaRepository<ExportJobJpaEntity, Long> {
    fun findByExportJobId(exportJobId: String): ExportJobJpaEntity?
}
