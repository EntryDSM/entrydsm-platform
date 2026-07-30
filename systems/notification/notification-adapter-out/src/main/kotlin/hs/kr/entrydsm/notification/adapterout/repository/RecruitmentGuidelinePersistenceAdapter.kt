package hs.kr.entrydsm.notification.adapterout.repository

import hs.kr.entrydsm.notification.application.port.out.RecruitmentGuidelineRepository
import hs.kr.entrydsm.notification.domain.model.RecruitmentGuideline
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

@Repository
@Transactional(readOnly = true)
class RecruitmentGuidelinePersistenceAdapter(
    private val recruitmentGuidelineJpaRepository: RecruitmentGuidelineJpaRepository,
) : RecruitmentGuidelineRepository {
    override fun findCurrent(): RecruitmentGuideline? =
        recruitmentGuidelineJpaRepository.findTopByOrderByCreatedAtDesc()?.toDomain()
}

