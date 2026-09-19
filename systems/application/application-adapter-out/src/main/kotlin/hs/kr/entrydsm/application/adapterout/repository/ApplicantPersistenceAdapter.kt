package hs.kr.entrydsm.application.adapterout.repository

import hs.kr.entrydsm.application.adapterout.entity.ApplicantJpaEntity
import hs.kr.entrydsm.application.application.exception.ApplicantNotFoundException
import hs.kr.entrydsm.application.application.port.out.ApplicantRepository
import hs.kr.entrydsm.application.domain.model.Applicant
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

@Repository
@Transactional
class ApplicantPersistenceAdapter(
    private val applicantJpaRepository: ApplicantJpaRepository,
) : ApplicantRepository {
    override fun save(applicant: Applicant): Applicant {
        val entity = if (applicant.id > 0) {
            applicantJpaRepository.findById(applicant.id)
                .orElseThrow { ApplicantNotFoundException(applicant.id) }
                .apply { updateFrom(applicant) }
        } else {
            ApplicantJpaEntity.from(applicant)
        }

        return applicantJpaRepository.saveAndFlush(entity).toDomain()
    }

    @Transactional(readOnly = true)
    override fun findById(id: Long): Applicant? =
        applicantJpaRepository.findById(id).orElse(null)?.toDomain()

    @Transactional(readOnly = true)
    override fun findByAccountId(accountId: Long): Applicant? =
        applicantJpaRepository.findByAccountId(accountId)?.toDomain()
}
