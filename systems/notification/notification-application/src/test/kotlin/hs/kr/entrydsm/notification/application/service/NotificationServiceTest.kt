package hs.kr.entrydsm.notification.application.service

import hs.kr.entrydsm.notification.application.exception.NotificationNotFoundException
import hs.kr.entrydsm.notification.application.port.`in`.command.ReadNotificationPageCommand
import hs.kr.entrydsm.notification.application.port.out.FaqRepository
import hs.kr.entrydsm.notification.application.port.out.NoticeRepository
import hs.kr.entrydsm.notification.application.port.out.RecruitmentGuidelineRepository
import hs.kr.entrydsm.notification.application.port.out.data.PageData
import hs.kr.entrydsm.notification.domain.model.Faq
import hs.kr.entrydsm.notification.domain.model.Notice
import hs.kr.entrydsm.notification.domain.model.RecruitmentGuideline
import hs.kr.entrydsm.notification.domain.model.RecruitmentSchedule
import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationServiceTest {
    @Test
    fun getNoticesReturnsPagedNoticesOrderedByCreatedAtDesc() {
        val service = service(
            notices = listOf(
                notice(id = 1L, title = "older", createdAt = now.minusDays(1)),
                notice(id = 2L, title = "newer", createdAt = now),
            ),
        )

        val result = service.getNotices(ReadNotificationPageCommand(page = 0, size = 1))

        assertEquals(1, result.content.size)
        assertEquals(2L, result.content.first().noticeId)
        assertEquals(2L, result.totalElements)
        assertEquals(2, result.totalPages)
        assertFalse(result.last)
    }

    @Test
    fun getNoticeReturnsDetailFields() {
        val service = service(
            notices = listOf(
                notice(
                    id = 1L,
                    title = "notice title",
                    content = "notice content",
                    author = "admin",
                    viewCount = 12,
                    createdAt = now.minusDays(2),
                    updatedAt = now.minusDays(1),
                ),
            ),
        )

        val result = service.getNotice(1L)

        assertEquals(1L, result.noticeId)
        assertEquals("notice title", result.title)
        assertEquals("notice content", result.content)
        assertEquals("admin", result.author)
        assertEquals(12, result.viewCount)
        assertEquals(now.minusDays(2), result.createdAt)
        assertEquals(now.minusDays(1), result.updatedAt)
    }

    @Test(expected = NotificationNotFoundException::class)
    fun getNoticeThrowsWhenNoticeDoesNotExist() {
        service().getNotice(1L)
    }

    @Test
    fun getFaqsReturnsPagedFaqsOrderedById() {
        val service = service(
            faqs = listOf(
                faq(id = 2L, question = "second"),
                faq(id = 1L, question = "first"),
            ),
        )

        val result = service.getFaqs(ReadNotificationPageCommand(page = 0, size = 1))

        assertEquals(1, result.content.size)
        assertEquals(1L, result.content.first().faqId)
        assertEquals("first", result.content.first().question)
        assertEquals(2L, result.totalElements)
        assertFalse(result.last)
    }

    @Test(expected = NotificationNotFoundException::class)
    fun getFaqThrowsWhenFaqDoesNotExist() {
        service().getFaq(1L)
    }

    @Test
    fun getRecruitmentGuidelineReturnsCurrentGuideline() {
        val guideline = RecruitmentGuideline(
            id = 1L,
            title = "2027 admission",
            description = "guideline",
            schedule = RecruitmentSchedule(
                applicationStart = LocalDate.parse("2026-10-19"),
                applicationEnd = LocalDate.parse("2026-10-23"),
                resultAt = LocalDate.parse("2026-10-30"),
            ),
            createdAt = now.minusDays(3),
            updatedAt = now.minusDays(2),
        )

        val result = service(guideline = guideline).getRecruitmentGuideline()

        assertEquals(1L, result.recruitmentId)
        assertEquals("2027 admission", result.title)
        assertEquals("guideline", result.description)
        assertEquals(LocalDate.parse("2026-10-19"), result.schedule.applicationStart)
        assertEquals(LocalDate.parse("2026-10-23"), result.schedule.applicationEnd)
        assertEquals(LocalDate.parse("2026-10-30"), result.schedule.resultAt)
        assertEquals(now.minusDays(3), result.createdAt)
        assertEquals(now.minusDays(2), result.updatedAt)
    }

    @Test
    fun getNoticesHandlesLargePageWithoutIntOverflow() {
        val result = service(notices = listOf(notice(id = 1L)))
            .getNotices(ReadNotificationPageCommand(page = Int.MAX_VALUE, size = Int.MAX_VALUE))

        assertTrue(result.content.isEmpty())
        assertEquals(1L, result.totalElements)
        assertEquals(1, result.totalPages)
        assertTrue(result.last)
    }

    @Test(expected = IllegalArgumentException::class)
    fun commandRejectsNegativePage() {
        ReadNotificationPageCommand(page = -1, size = 10)
    }

    @Test(expected = IllegalArgumentException::class)
    fun commandRejectsZeroSize() {
        ReadNotificationPageCommand(page = 0, size = 0)
    }

    private fun service(
        notices: List<Notice> = emptyList(),
        faqs: List<Faq> = emptyList(),
        guideline: RecruitmentGuideline? = null,
    ): NotificationService =
        NotificationService(
            noticeRepository = FakeNoticeRepository(notices),
            faqRepository = FakeFaqRepository(faqs),
            recruitmentGuidelineRepository = FakeRecruitmentGuidelineRepository(guideline),
        )

    private inner class FakeNoticeRepository(
        private val notices: List<Notice> = emptyList(),
    ) : NoticeRepository {
        override fun findPage(command: ReadNotificationPageCommand): PageData<Notice> {
            val sorted = notices.sortedWith(compareByDescending<Notice> { it.createdAt }.thenByDescending { it.id })
            return sorted.toPageData(command)
        }

        override fun findById(id: Long): Notice? = notices.firstOrNull { it.id == id }
    }

    private inner class FakeFaqRepository(
        private val faqs: List<Faq> = emptyList(),
    ) : FaqRepository {
        override fun findPage(command: ReadNotificationPageCommand): PageData<Faq> =
            faqs.sortedBy { it.id }.toPageData(command)

        override fun findById(id: Long): Faq? = faqs.firstOrNull { it.id == id }
    }

    private class FakeRecruitmentGuidelineRepository(
        private val guideline: RecruitmentGuideline? = null,
    ) : RecruitmentGuidelineRepository {
        override fun findCurrent(): RecruitmentGuideline? = guideline
    }

    private fun notice(
        id: Long,
        title: String = "title",
        content: String = "content",
        author: String = "admin",
        viewCount: Int = 0,
        createdAt: LocalDateTime = now,
        updatedAt: LocalDateTime = now,
    ): Notice =
        Notice(
            id = id,
            title = title,
            content = content,
            author = author,
            viewCount = viewCount,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )

    private fun faq(
        id: Long,
        category: String = "입학 문의",
        question: String = "question",
        answer: String = "answer",
        viewCount: Int = 0,
        createdAt: LocalDateTime = now,
        updatedAt: LocalDateTime = now,
    ): Faq =
        Faq(
            id = id,
            category = category,
            question = question,
            answer = answer,
            viewCount = viewCount,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )

    private fun <T> List<T>.toPageData(command: ReadNotificationPageCommand): PageData<T> {
        val fromIndex = command.offset()
            .coerceAtMost(size.toLong())
            .toInt()
        val toIndex = (fromIndex.toLong() + command.size.toLong())
            .coerceAtMost(size.toLong())
            .toInt()
        return PageData(
            content = subList(fromIndex, toIndex),
            page = command.page,
            size = command.size,
            totalElements = size.toLong(),
        )
    }

    private companion object {
        val now: LocalDateTime = LocalDateTime.parse("2026-08-17T09:00:00")
    }
}
