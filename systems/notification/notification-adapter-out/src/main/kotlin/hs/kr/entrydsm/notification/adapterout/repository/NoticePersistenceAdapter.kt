package hs.kr.entrydsm.notification.adapterout.repository

import hs.kr.entrydsm.notification.adapterout.entity.NoticeJpaEntity
import hs.kr.entrydsm.notification.application.port.`in`.command.CreateNoticeCommand
import hs.kr.entrydsm.notification.application.port.`in`.command.ReadNotificationPageCommand
import hs.kr.entrydsm.notification.application.port.out.NoticeRepository
import hs.kr.entrydsm.notification.application.port.out.data.PageData
import hs.kr.entrydsm.notification.domain.model.Notice
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

@Repository
@Transactional(readOnly = true)
class NoticePersistenceAdapter(
    private val noticeJpaRepository: NoticeJpaRepository,
) : NoticeRepository {
    override fun findPage(command: ReadNotificationPageCommand): PageData<Notice> {
        val page = command.category?.let { category ->
            noticeJpaRepository.findAllByCategoryOrderByCreatedAtDescIdDesc(
                category,
                command.toPageRequest(),
            )
        } ?: noticeJpaRepository.findAllByOrderByCreatedAtDescIdDesc(command.toPageRequest())
        return PageData(
            content = page.content.map { it.toDomain() },
            page = command.page,
            size = command.size,
            totalElements = page.totalElements,
        )
    }

    override fun findById(id: Long): Notice? =
        noticeJpaRepository.findById(id).orElse(null)?.toDomain()

    @Transactional
    override fun create(command: CreateNoticeCommand): Notice =
        noticeJpaRepository.save(
            NoticeJpaEntity(
                title = command.title,
                content = command.content,
                category = command.category,
                author = command.author,
                isPinned = command.isPinned,
                attachmentIds = command.attachmentIds.takeIf { it.isNotEmpty() }?.joinToString(","),
            ),
        ).toDomain()

    private fun ReadNotificationPageCommand.toPageRequest(): PageRequest =
        PageRequest.of(page, size)
}

