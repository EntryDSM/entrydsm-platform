package hs.kr.entrydsm.application.adapterout.repository

import hs.kr.entrydsm.application.adapterout.entity.PassResultId
import hs.kr.entrydsm.application.adapterout.entity.PassResultJpaEntity
import hs.kr.entrydsm.application.application.port.out.PassResultRepository
import hs.kr.entrydsm.application.domain.model.PassResult
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

@Repository
@Transactional
class PassResultPersistenceAdapter(
    private val passResultJpaRepository: PassResultJpaRepository,
    private val applicantJpaRepository: ApplicantJpaRepository,
) : PassResultRepository {

    override fun upsertAll(results: List<PassResult>): Int {
        val entities = results.map { result ->
            val id = PassResultId(result.applicantId, result.resultType)
            (passResultJpaRepository.findById(id).orElse(null) ?: PassResultJpaEntity(id = id)).apply {
                applicant = applicantJpaRepository.getReferenceById(result.applicantId)
                this.result = result.result
                processedBy = result.processedBy
                processedAt = result.processedAt
            }
        }

        return passResultJpaRepository.saveAll(entities).size
    }
}
