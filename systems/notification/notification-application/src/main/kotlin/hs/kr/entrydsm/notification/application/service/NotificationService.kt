package hs.kr.entrydsm.notification.application.service

import hs.kr.entrydsm.notification.application.exception.NotificationNotFoundException
import hs.kr.entrydsm.notification.application.port.`in`.NotificationPort
import hs.kr.entrydsm.notification.application.port.`in`.command.ReadFaqPageCommand
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
import hs.kr.entrydsm.notification.application.port.out.data.PageData
import hs.kr.entrydsm.notification.domain.model.Faq
import hs.kr.entrydsm.notification.domain.model.Notice

class NotificationService(
    private val noticeRepository: NoticeRepository,
    private val faqRepository: FaqRepository,
    private val recruitmentGuidelineRepository: RecruitmentGuidelineRepository,
) : NotificationPort {
    override fun getNotices(command: ReadNotificationPageCommand): PageResult<NoticeSummaryResult> =
        noticeRepository.findPage(command).toResult { it.toSummaryResult() }

    override fun getNotice(id: Long): NoticeDetailResult =
        noticeRepository.findById(id)?.toDetailResult()
            ?: throw NotificationNotFoundException("notice not found: id=$id")

    override fun getFaqs(command: ReadFaqPageCommand): PageResult<FaqSummaryResult> =
        faqRepository.findPage(command).toResult { it.toSummaryResult() }

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

    private fun <T, R> PageData<T>.toResult(
        mapper: (T) -> R,
    ): PageResult<R> {
        return PageResult(
            content = content.map(mapper),
            page = page,
            size = size,
            totalElements = totalElements,
            totalPages = totalPages,
            last = last,
        )
    }
}
