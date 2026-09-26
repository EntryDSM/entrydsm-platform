package hs.kr.entrydsm.admin.adapterout.persistence

import hs.kr.entrydsm.admin.adapterout.entity.AdmissionQuotaJpaEntity
import hs.kr.entrydsm.admin.adapterout.entity.ExportJobJpaEntity
import hs.kr.entrydsm.admin.adapterout.entity.ScorePolicyJpaEntity
import hs.kr.entrydsm.admin.adapterout.repository.AdmissionQuotaJpaRepository
import hs.kr.entrydsm.admin.adapterout.repository.ExportJobJpaRepository
import hs.kr.entrydsm.admin.adapterout.repository.ScorePolicyJpaRepository
import hs.kr.entrydsm.admin.domain.enum.ExportStatus
import hs.kr.entrydsm.admin.domain.enum.ExportType
import hs.kr.entrydsm.admin.domain.model.AdmissionQuota
import hs.kr.entrydsm.admin.domain.model.ExportJob
import hs.kr.entrydsm.admin.domain.model.ScorePolicy
import hs.kr.entrydsm.admin.domain.port.out.AdmissionQuotaRepository
import hs.kr.entrydsm.admin.domain.port.out.ExportJobRepository
import hs.kr.entrydsm.admin.domain.port.out.ScorePolicyRepository
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
 * 전형별 행 전체를 지우고 다시 넣습니다. 3행 규모라 upsert보다 단순한 쪽을 택했다.
 */
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

@Component
class ExportJobPersistenceAdapter(
    private val exportJobJpaRepository: ExportJobJpaRepository,
) : ExportJobRepository {

    override fun findByExportJobId(exportJobId: String): ExportJob? =
        exportJobJpaRepository.findByExportJobId(exportJobId)?.toDomain()

    override fun findDownloadableByType(type: ExportType): List<ExportJob> =
        exportJobJpaRepository.findAllByTypeAndStatusAndObjectKeyIsNotNull(type, ExportStatus.COMPLETED)
            .map { it.toDomain() }

    /**
     * 필터는 테이블에 컬럼이 없어 엔티티를 거치면 사라집니다. 처리기가 반환값의 필터로
     * 지원자를 고르므로 넘겨받은 필터를 다시 담아 돌려줍니다.
     *
     * ponytail: 필터를 DB 에 남기지 않아 재시작 뒤 작업을 다시 처리할 수 없다. 재처리가
     * 필요해지면 export_job 에 필터 컬럼을 추가한다.
     */
    override fun save(exportJob: ExportJob): ExportJob =
        exportJobJpaRepository.save(ExportJobJpaEntity.from(exportJob)).toDomain()
            .copy(filter = exportJob.filter)
}
