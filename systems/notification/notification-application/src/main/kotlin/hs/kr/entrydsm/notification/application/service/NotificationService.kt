package hs.kr.entrydsm.notification.application.service

import hs.kr.entrydsm.notification.application.exception.NotificationNotFoundException
import hs.kr.entrydsm.notification.application.port.`in`.NotificationPort
import hs.kr.entrydsm.notification.application.port.`in`.command.ReadNotificationPageCommand
import hs.kr.entrydsm.notification.application.port.`in`.result.FaqDetailResult
import hs.kr.entrydsm.notification.application.port.`in`.result.FaqSummaryResult
import hs.kr.entrydsm.notification.application.port.`in`.result.NoticeDetailResult
import hs.kr.entrydsm.notification.application.port.`in`.result.NoticeSummaryResult
import hs.kr.entrydsm.notification.application.port.`in`.result.PageResult
import hs.kr.entrydsm.notification.application.port.`in`.result.RecruitmentGuidelineResult
import hs.kr.entrydsm.notification.application.port.`in`.result.RecruitmentScheduleResult
import hs.kr.entrydsm.notification.application.port.out.FaqRepository
import hs.kr.entrydsm.notification.application.port.out.NoticeRepository
import hs.kr.entrydsm.notification.application.port.out.RecruitmentGuidelineRepository
import hs.kr.entrydsm.notification.domain.model.Faq
import hs.kr.entrydsm.notification.domain.model.Notice
import org.springframework.stereotype.Service

@Service
class NotificationService(
    private val noticeRepository: NoticeRepository,
    private val faqRepository: FaqRepository,
    private val recruitmentGuidelineRepository: RecruitmentGuidelineRepository,
) : NotificationPort {
    override fun getNotices(command: ReadNotificationPageCommand): PageResult<NoticeSummaryResult> =
        noticeRepository.findAll()
            .sortedByDescending { it.createdAt }
            .toPage(command) { it.toSummaryResult() }

    override fun getNotice(id: Long): NoticeDetailResult =
        noticeRepository.findById(id)?.toDetailResult()
            ?: throw NotificationNotFoundException("notice not found: id=$id")

    override fun getFaqs(command: ReadNotificationPageCommand): PageResult<FaqSummaryResult> =
        faqRepository.findAll()
            .sortedBy { it.id }
            .toPage(command) { it.toSummaryResult() }

    override fun getFaq(id: Long): FaqDetailResult =
        faqRepository.findById(id)?.toDetailResult()
            ?: throw NotificationNotFoundException("faq not found: id=$id")

    override fun getRecruitmentGuideline(): RecruitmentGuidelineResult {
        val guideline = recruitmentGuidelineRepository.findCurrent()
            ?: throw NotificationNotFoundException("recruitment guideline not found")
        return RecruitmentGuidelineResult(
            recruitmentId = guideline.id,
            title = guideline.title,
            description = guideline.description,
            schedule = RecruitmentScheduleResult(
                applicationStart = guideline.schedule.applicationStart,
                applicationEnd = guideline.schedule.applicationEnd,
                resultAt = guideline.schedule.resultAt,
            ),
            createdAt = guideline.createdAt,
            updatedAt = guideline.updatedAt,
        )
    }

    private fun Notice.toSummaryResult(): NoticeSummaryResult =
        NoticeSummaryResult(
            noticeId = id,
            title = title,
            author = author,
            createdAt = createdAt,
        )

    private fun Notice.toDetailResult(): NoticeDetailResult =
        NoticeDetailResult(
            noticeId = id,
            title = title,
            content = content,
            author = author,
            viewCount = viewCount,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )

    private fun Faq.toSummaryResult(): FaqSummaryResult =
        FaqSummaryResult(
            faqId = id,
            category = category,
            question = question,
            answer = answer,
        )

    private fun Faq.toDetailResult(): FaqDetailResult =
        FaqDetailResult(
            faqId = id,
            category = category,
            question = question,
            answer = answer,
            viewCount = viewCount,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )

    private fun <T, R> List<T>.toPage(
        command: ReadNotificationPageCommand,
        mapper: (T) -> R,
    ): PageResult<R> {
        require(command.page >= 0) { "page must be greater than or equal to 0" }
        require(command.size > 0) { "size must be greater than 0" }

        val fromIndex = (command.page * command.size).coerceAtMost(size)
        val toIndex = (fromIndex + command.size).coerceAtMost(size)
        val totalPages = if (isEmpty()) 0 else ((size - 1) / command.size) + 1
        return PageResult(
            content = subList(fromIndex, toIndex).map(mapper),
            page = command.page,
            size = command.size,
            totalElements = size.toLong(),
            totalPages = totalPages,
            last = command.page >= totalPages - 1,
        )
    }
}
