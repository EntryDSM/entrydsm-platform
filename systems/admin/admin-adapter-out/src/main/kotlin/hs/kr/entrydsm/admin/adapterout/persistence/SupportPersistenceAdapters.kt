package hs.kr.entrydsm.admin.adapterout.persistence

import hs.kr.entrydsm.admin.adapterout.entity.AdmissionQuotaJpaEntity
import hs.kr.entrydsm.admin.adapterout.entity.ExportJobJpaEntity
import hs.kr.entrydsm.admin.adapterout.entity.QuestionAnswerJpaEntity
import hs.kr.entrydsm.admin.adapterout.entity.ScorePolicyJpaEntity
import hs.kr.entrydsm.admin.adapterout.repository.AdmissionQuotaJpaRepository
import hs.kr.entrydsm.admin.adapterout.repository.ExportJobJpaRepository
import hs.kr.entrydsm.admin.adapterout.repository.QuestionAnswerJpaRepository
import hs.kr.entrydsm.admin.adapterout.repository.ScorePolicyJpaRepository
import hs.kr.entrydsm.admin.domain.model.AdmissionQuota
import hs.kr.entrydsm.admin.domain.model.ExportJob
import hs.kr.entrydsm.admin.domain.model.QuestionAnswer
import hs.kr.entrydsm.admin.domain.model.ScorePolicy
import hs.kr.entrydsm.admin.domain.port.out.AdmissionQuotaRepository
import hs.kr.entrydsm.admin.domain.port.out.ExportJobRepository
import hs.kr.entrydsm.admin.domain.port.out.QuestionAnswerRepository
import hs.kr.entrydsm.admin.domain.port.out.ScorePolicyRepository
import java.time.Clock
import java.time.Instant
import org.springframework.stereotype.Component

@Component
class ScorePolicyPersistenceAdapter(
    private val scorePolicyJpaRepository: ScorePolicyJpaRepository,
) : ScorePolicyRepository {

    override fun findCurrent(): ScorePolicy? =
        scorePolicyJpaRepository.findTopByOrderByPolicyVersionDesc()?.toDomain()

    override fun save(scorePolicy: ScorePolicy): ScorePolicy =
        scorePolicyJpaRepository.save(ScorePolicyJpaEntity.from(scorePolicy)).toDomain()
}

/**
 * 지역 × 전형 행 전체를 지우고 다시 넣습니다. 6행 규모라 upsert보다 단순한 쪽을 택했다.
 */
@Component
class AdmissionQuotaPersistenceAdapter(
    private val admissionQuotaJpaRepository: AdmissionQuotaJpaRepository,
) : AdmissionQuotaRepository {

    override fun find(): AdmissionQuota? =
        admissionQuotaJpaRepository.findAll().takeIf { it.isNotEmpty() }?.toDomain()

    override fun save(admissionQuota: AdmissionQuota): AdmissionQuota {
        admissionQuotaJpaRepository.deleteAllInBatch()
        val rows = admissionQuota.quotas.flatMap { (region, byType) ->
            byType.map { (type, quota) ->
                AdmissionQuotaJpaEntity(
                    region = region,
                    admissionType = type,
                    quota = quota,
                    updatedAt = admissionQuota.updatedAt,
                    updatedBy = admissionQuota.updatedBy,
                )
            }
        }
        return admissionQuotaJpaRepository.saveAll(rows).toDomain()
    }

    private fun List<AdmissionQuotaJpaEntity>.toDomain(): AdmissionQuota {
        val latest = maxBy { it.updatedAt }
        return AdmissionQuota(
            quotas = groupBy { it.region }
                .mapValues { (_, rows) -> rows.associate { it.admissionType to it.quota } },
            updatedAt = latest.updatedAt,
            updatedBy = latest.updatedBy,
        )
    }
}

@Component
class ExportJobPersistenceAdapter(
    private val exportJobJpaRepository: ExportJobJpaRepository,
) : ExportJobRepository {

    override fun findByExportJobId(exportJobId: String): ExportJob? =
        exportJobJpaRepository.findByExportJobId(exportJobId)?.toDomain()

    override fun save(exportJob: ExportJob): ExportJob =
        exportJobJpaRepository.save(ExportJobJpaEntity.from(exportJob)).toDomain()
}

@Component
class QuestionAnswerPersistenceAdapter(
    private val questionAnswerJpaRepository: QuestionAnswerJpaRepository,
    private val clock: Clock,
) : QuestionAnswerRepository {

    override fun save(questionAnswer: QuestionAnswer): QuestionAnswer =
        questionAnswerJpaRepository
            .save(QuestionAnswerJpaEntity.from(questionAnswer, Instant.now(clock)))
            .toDomain()
}
