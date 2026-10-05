package hs.kr.entrydsm.application.administration.adapterout.persistence

import hs.kr.entrydsm.application.administration.adapterout.entity.AdmissionQuotaJpaEntity
import hs.kr.entrydsm.application.administration.adapterout.entity.ExportJobJpaEntity
import hs.kr.entrydsm.application.administration.adapterout.entity.ScorePolicyJpaEntity
import hs.kr.entrydsm.application.administration.adapterout.repository.AdmissionQuotaJpaRepository
import hs.kr.entrydsm.application.administration.adapterout.repository.ExportJobJpaRepository
import hs.kr.entrydsm.application.administration.adapterout.repository.ScorePolicyJpaRepository
import hs.kr.entrydsm.admin.domain.enum.ExportStatus
import hs.kr.entrydsm.admin.domain.enum.ExportType
import hs.kr.entrydsm.admin.domain.model.AdmissionQuota
import hs.kr.entrydsm.admin.domain.model.ExportJob
import hs.kr.entrydsm.admin.domain.model.ScorePolicy
import hs.kr.entrydsm.admin.domain.port.out.AdmissionQuotaRepository
import hs.kr.entrydsm.admin.domain.port.out.ExportJobRepository
import hs.kr.entrydsm.admin.domain.port.out.ScorePolicyRepository
import java.time.Instant
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = ["application.administration.enabled"], havingValue = "true")
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
 * 전형별 행 전체를 지우고 다시 넣습니다. 3행 규모라 upsert보다 단순한 쪽을 택했다.
 */
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = ["application.administration.enabled"], havingValue = "true")
@Component
class AdmissionQuotaPersistenceAdapter(
    private val admissionQuotaJpaRepository: AdmissionQuotaJpaRepository,
) : AdmissionQuotaRepository {

    override fun find(): AdmissionQuota? =
        admissionQuotaJpaRepository.findAll().takeIf { it.isNotEmpty() }?.toDomain()

    override fun save(admissionQuota: AdmissionQuota): AdmissionQuota {
        admissionQuotaJpaRepository.deleteAllInBatch()
        val rows = admissionQuota.quotas.map { (type, quota) ->
            AdmissionQuotaJpaEntity(
                admissionType = type,
                quota = quota,
                updatedAt = admissionQuota.updatedAt,
                updatedBy = admissionQuota.updatedBy,
            )
        }
        return admissionQuotaJpaRepository.saveAll(rows).toDomain()
    }

    private fun List<AdmissionQuotaJpaEntity>.toDomain(): AdmissionQuota {
        val latest = maxBy { it.updatedAt }
        return AdmissionQuota(
            quotas = associate { it.admissionType to it.quota },
            updatedAt = latest.updatedAt,
            updatedBy = latest.updatedBy,
        )
    }
}

@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = ["application.administration.enabled"], havingValue = "true")
@Component
class ExportJobPersistenceAdapter(
    private val exportJobJpaRepository: ExportJobJpaRepository,
) : ExportJobRepository {

    override fun findByExportJobId(exportJobId: String): ExportJob? =
        exportJobJpaRepository.findByExportJobId(exportJobId)?.toDomain()

    @Transactional
    override fun findDownloadableByType(type: ExportType): List<ExportJob> =
        exportJobJpaRepository.findAllByTypeAndStatusAndObjectKeyIsNotNull(type, ExportStatus.COMPLETED)
            .map { it.toDomain() }

    @Transactional
    override fun claimNext(now: Instant, staleBefore: Instant): ExportJob? =
        exportJobJpaRepository.findClaimable(staleBefore)
            ?.toDomain()?.started(now)?.let(::save)

    override fun save(exportJob: ExportJob): ExportJob =
        exportJobJpaRepository.save(ExportJobJpaEntity.from(exportJob)).toDomain()
}
