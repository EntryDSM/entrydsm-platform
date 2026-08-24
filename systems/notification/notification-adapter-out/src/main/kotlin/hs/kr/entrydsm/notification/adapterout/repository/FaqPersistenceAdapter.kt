package hs.kr.entrydsm.notification.adapterout.repository

import hs.kr.entrydsm.notification.application.port.`in`.command.ReadFaqPageCommand
import hs.kr.entrydsm.notification.application.port.out.FaqRepository
import hs.kr.entrydsm.notification.application.port.out.data.PageData
import hs.kr.entrydsm.notification.domain.model.Faq
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

@Repository
@Transactional(readOnly = true)
class FaqPersistenceAdapter(
    private val faqJpaRepository: FaqJpaRepository,
) : FaqRepository {
    override fun findPage(command: ReadFaqPageCommand): PageData<Faq> {
        val page = command.category?.let { category ->
            faqJpaRepository.findAllByCategoryOrderByIdAsc(category.label, command.toPageRequest())
        } ?: faqJpaRepository.findAllByOrderByIdAsc(command.toPageRequest())
        return PageData(
            content = page.content.map { it.toDomain() },
            page = command.page,
            size = command.size,
            totalElements = page.totalElements,
        )
    }

    override fun findById(id: Long): Faq? =
        faqJpaRepository.findById(id).orElse(null)?.toDomain()

    private fun ReadFaqPageCommand.toPageRequest(): PageRequest =
        PageRequest.of(page, size)
}

