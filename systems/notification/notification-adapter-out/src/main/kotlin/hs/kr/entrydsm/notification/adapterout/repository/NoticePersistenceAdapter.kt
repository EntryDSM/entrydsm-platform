package hs.kr.entrydsm.notification.adapterout.repository

import hs.kr.entrydsm.notification.adapterout.entity.NoticeJpaEntity
import hs.kr.entrydsm.notification.application.port.`in`.command.CreateNoticeCommand
import hs.kr.entrydsm.notification.application.port.`in`.command.ReadNotificationPageCommand
import hs.kr.entrydsm.notification.application.port.`in`.command.UpdateNoticeCommand
import hs.kr.entrydsm.notification.application.port.out.NoticeRepository
import hs.kr.entrydsm.notification.application.port.out.data.PageData
import hs.kr.entrydsm.notification.domain.model.Notice
import java.time.LocalDateTime
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
                attachmentIds = command.attachmentIds.toColumn(),
            ),
        ).toDomain()

    @Transactional
    override fun update(command: UpdateNoticeCommand): Notice? {
        val entity = noticeJpaRepository.findById(command.noticeId).orElse(null) ?: return null
        command.title?.let { entity.title = it }
        command.content?.let { entity.content = it }
        command.category?.let { entity.category = it }
        command.isPinned?.let { entity.isPinned = it }
        command.attachmentIds?.let { entity.attachmentIds = it.toColumn() }
        entity.updatedAt = LocalDateTime.now()
        return noticeJpaRepository.save(entity).toDomain()
    }

    @Transactional
    override fun deleteById(id: Long): Boolean {
        val entity = noticeJpaRepository.findById(id).orElse(null) ?: return false
        noticeJpaRepository.delete(entity)
        return true
    }

    private fun ReadNotificationPageCommand.toPageRequest(): PageRequest =
        PageRequest.of(page, size)

    /** 첨부 식별자는 쉼표로 이어 저장하고, 없으면 NULL 로 둔다. */
    private fun List<String>.toColumn(): String? =
        takeIf { it.isNotEmpty() }?.joinToString(",")
}

