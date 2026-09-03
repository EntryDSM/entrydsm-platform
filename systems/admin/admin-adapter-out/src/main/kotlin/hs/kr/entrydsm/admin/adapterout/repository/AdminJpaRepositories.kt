package hs.kr.entrydsm.admin.adapterout.repository

import hs.kr.entrydsm.admin.adapterout.entity.ApplicantJpaEntity
import hs.kr.entrydsm.admin.adapterout.entity.ExportJobJpaEntity
import hs.kr.entrydsm.admin.adapterout.entity.NoticeJpaEntity
import hs.kr.entrydsm.admin.adapterout.entity.QuestionAnswerJpaEntity
import hs.kr.entrydsm.admin.adapterout.entity.ScorePolicyJpaEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor

interface ApplicantJpaRepository :
    JpaRepository<ApplicantJpaEntity, Long>,
    JpaSpecificationExecutor<ApplicantJpaEntity>

interface ScorePolicyJpaRepository : JpaRepository<ScorePolicyJpaEntity, Long> {
    fun findTopByOrderByPolicyVersionDesc(): ScorePolicyJpaEntity?
}

interface ExportJobJpaRepository : JpaRepository<ExportJobJpaEntity, Long> {
    fun findByExportJobId(exportJobId: String): ExportJobJpaEntity?
}

interface NoticeJpaRepository : JpaRepository<NoticeJpaEntity, Long>

interface QuestionAnswerJpaRepository : JpaRepository<QuestionAnswerJpaEntity, Long>
