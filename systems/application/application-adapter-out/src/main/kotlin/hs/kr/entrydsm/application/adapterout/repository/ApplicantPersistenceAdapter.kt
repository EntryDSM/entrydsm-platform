package hs.kr.entrydsm.application.adapterout.repository

import hs.kr.entrydsm.application.adapterout.entity.ApplicantJpaEntity
import hs.kr.entrydsm.application.application.exception.ApplicantNotFoundException
import hs.kr.entrydsm.application.application.port.out.ApplicantRepository
import hs.kr.entrydsm.application.domain.enum.ApplicantStatus
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

    @Transactional(readOnly = true)
    override fun existingIds(ids: Collection<Long>): Set<Long> =
        if (ids.isEmpty()) emptySet() else applicantJpaRepository.findExistingIds(ids).toSet()

    @Transactional(readOnly = true)
    override fun findAllSubmitted(): List<Applicant> =
        applicantJpaRepository.findAllByStatusInWithRecord(SUBMITTED_STATUSES)
            .map(ApplicantJpaEntity::toDomain)

    private companion object {
        /** 취소한 원서와 아직 작성 중인 원서는 전형 대상이 아니다. */
        val SUBMITTED_STATUSES = listOf(
            ApplicantStatus.SUBMITTED,
            ApplicantStatus.REVIEWING,
            ApplicantStatus.COMPLETED,
        )
    }
}
