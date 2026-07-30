package hs.kr.entrydsm.notification.adapterout.repository

import hs.kr.entrydsm.notification.application.port.out.FaqRepository
import hs.kr.entrydsm.notification.domain.model.Faq
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

@Repository
@Transactional(readOnly = true)
class FaqPersistenceAdapter(
    private val faqJpaRepository: FaqJpaRepository,
) : FaqRepository {
    override fun findAll(): List<Faq> =
        faqJpaRepository.findAllByOrderByIdAsc().map { it.toDomain() }

    override fun findById(id: Long): Faq? =
        faqJpaRepository.findById(id).orElse(null)?.toDomain()
}

