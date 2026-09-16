package hs.kr.entrydsm.notification.adapterin

import hs.kr.entrydsm.notification.adapterin.api.NotificationApiAdapter
import hs.kr.entrydsm.notification.adapterin.web.exception.GlobalExceptionHandler
import hs.kr.entrydsm.notification.api.AnswerQuestionRequest
import hs.kr.entrydsm.notification.api.CreateNoticeRequest
import hs.kr.entrydsm.notification.api.NotificationApiException
import hs.kr.entrydsm.notification.api.UpdateNoticeRequest
import hs.kr.entrydsm.notification.application.exception.NotificationNotFoundException
import hs.kr.entrydsm.notification.application.port.`in`.NotificationPort
import hs.kr.entrydsm.notification.application.port.`in`.command.AnswerQuestionCommand
import hs.kr.entrydsm.notification.application.port.`in`.command.CreateNoticeCommand
import hs.kr.entrydsm.notification.application.port.`in`.command.ReadFaqPageCommand
import hs.kr.entrydsm.notification.application.port.`in`.command.ReadNotificationPageCommand
import hs.kr.entrydsm.notification.application.port.`in`.command.UpdateNoticeCommand
import hs.kr.entrydsm.notification.application.port.`in`.result.FaqDetailResult
import hs.kr.entrydsm.notification.application.port.`in`.result.NoticeDetailResult
import hs.kr.entrydsm.notification.domain.model.NoticeCategory
import java.time.LocalDateTime
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.springframework.http.HttpStatus

class NotificationAdapterInModuleTest {
    @Test
    fun moduleLoads() {
        assertTrue(true)
    }

    @Test
    fun createNoticePassesAllFieldsToPort() {
        val port = RecordingNotificationPort()

        val created = NotificationApiAdapter(port).createNotice(noticeRequest(category = "PROSPECTIVE_STUDENT"))

        val command = port.created!!
        assertEquals("title", command.title)
        assertEquals("content", command.content)
        assertEquals(NoticeCategory.PROSPECTIVE_STUDENT, command.category)
        assertEquals("관리자", command.author)
        assertTrue(command.isPinned)
        assertEquals(listOf("doc_1", "doc_2"), command.attachmentIds)
        assertEquals(1L, created.noticeId)
        assertEquals(CREATED_AT.toInstant(ZoneOffset.UTC), created.createdAt)
    }

    /** Notion 명세가 예시로 쓰는 영문 이름도 같은 분류로 치환해 받는다. */
    @Test
    fun createNoticeAcceptsSpecCategoryNames() {
        val port = RecordingNotificationPort()
        val adapter = NotificationApiAdapter(port)

        adapter.createNotice(noticeRequest(category = "Admissions Notice"))
        assertEquals(NoticeCategory.ADMISSION_NOTICE, port.created?.category)

        adapter.createNotice(noticeRequest(category = "Prospective Students Notice"))
        assertEquals(NoticeCategory.PROSPECTIVE_STUDENT, port.created?.category)

        adapter.createNotice(noticeRequest(category = "  admissions NOTICE  "))
        assertEquals(NoticeCategory.ADMISSION_NOTICE, port.created?.category)
    }

    @Test
    fun createNoticeAcceptsKoreanCategoryNames() {
        val port = RecordingNotificationPort()

        NotificationApiAdapter(port).createNotice(noticeRequest(category = "예비 신입생 안내"))

        assertEquals(NoticeCategory.PROSPECTIVE_STUDENT, port.created?.category)
    }

    @Test
    fun createNoticeRejectsUnknownCategory() {
        val port = RecordingNotificationPort()

        assertThrows(NotificationApiException.InvalidArgument::class.java) {
            NotificationApiAdapter(port).createNotice(noticeRequest(category = "Graduation Notice"))
        }

        assertNull(port.created)
    }

    /** 보내지 않은 필드는 null 로 넘겨 기존 값을 유지하게 한다. 빈 첨부 목록은 보낸 값이다. */
    @Test
    fun updateNoticeLeavesAbsentFieldsNull() {
        val port = RecordingNotificationPort()

        val updated = NotificationApiAdapter(port).updateNotice(
            UpdateNoticeRequest(noticeId = 3L, title = "new title", attachmentIds = emptyList()),
        )

        val command = port.updated!!
        assertEquals(3L, command.noticeId)
        assertEquals("new title", command.title)
        assertNull(command.content)
        assertNull(command.category)
        assertNull(command.isPinned)
        assertEquals(emptyList<String>(), command.attachmentIds)
        assertEquals(3L, updated.noticeId)
        assertEquals(UPDATED_AT.toInstant(ZoneOffset.UTC), updated.updatedAt)
    }

    /** false 도 보낸 값이라 버리지 않는다. 첨부를 보내지 않으면 null 이다. */
    @Test
    fun updateNoticeKeepsExplicitFalseAndAcceptsSpecCategoryName() {
        val port = RecordingNotificationPort()

        NotificationApiAdapter(port).updateNotice(
            UpdateNoticeRequest(
                noticeId = 3L,
                content = "new content",
                category = "Prospective Students Notice",
                isPinned = false,
            ),
        )

        val command = port.updated!!
        assertNull(command.title)
        assertEquals("new content", command.content)
        assertEquals(NoticeCategory.PROSPECTIVE_STUDENT, command.category)
        assertEquals(false, command.isPinned)
        assertNull(command.attachmentIds)
    }

    @Test
    fun updateNoticeRejectsUnknownCategory() {
        val port = RecordingNotificationPort()

        assertThrows(NotificationApiException.InvalidArgument::class.java) {
            NotificationApiAdapter(port).updateNotice(UpdateNoticeRequest(noticeId = 3L, category = "Graduation Notice"))
        }

        assertNull(port.updated)
    }

    @Test
    fun deleteNoticePassesIdToPort() {
        val port = RecordingNotificationPort()

        NotificationApiAdapter(port).deleteNotice(3L)

        assertEquals(3L, port.deletedId)
    }

    @Test
    fun deleteNoticeReportsMissingNoticeAsNotFound() {
        val port = RecordingNotificationPort(NotificationNotFoundException("notice not found: id=404"))

        val exception = assertThrows(NotificationApiException.NotFound::class.java) {
            NotificationApiAdapter(port).deleteNotice(404L)
        }

        assertEquals("notice not found: id=404", exception.message)
    }

    @Test
    fun answerQuestionPassesAllFieldsToPort() {
        val port = RecordingNotificationPort()

        val answered = NotificationApiAdapter(port).answerQuestion(answerRequest(questionId = 7L))

        val command = port.answered!!
        assertEquals(7L, command.questionId)
        assertEquals("answer", command.content)
        assertEquals("admin", command.answeredBy)
        assertEquals(7L, answered.questionId)
        assertEquals(ANSWERED_AT.toInstant(ZoneOffset.UTC), answered.answeredAt)
    }

    @Test
    fun answerQuestionReportsMissingQuestionAsNotFound() {
        val port = RecordingNotificationPort(NotificationNotFoundException("faq not found: id=404"))

        assertThrows(NotificationApiException.NotFound::class.java) {
            NotificationApiAdapter(port).answerQuestion(answerRequest(questionId = 404L))
        }
    }

    /** 저장소 예외는 API 예외로 감싸지 않는다. 호출 모듈이 자기 형식으로 500 을 만든다. */
    @Test
    fun answerQuestionPropagatesUnexpectedFailures() {
        val port = RecordingNotificationPort(RuntimeException("Table 'entrydsm.faqs' doesn't exist"))

        val exception = assertThrows(RuntimeException::class.java) {
            NotificationApiAdapter(port).answerQuestion(answerRequest(questionId = 7L))
        }

        assertEquals("Table 'entrydsm.faqs' doesn't exist", exception.message)
        assertTrue(exception !is NotificationApiException)
    }

    @Test
    fun answerQuestionRejectsBlankContent() {
        val port = RecordingNotificationPort()

        assertThrows(NotificationApiException.InvalidArgument::class.java) {
            NotificationApiAdapter(port).answerQuestion(answerRequest(questionId = 7L, content = " "))
        }

        assertNull(port.answered)
    }

    @Test
    fun notFoundExceptionReturnsStableErrorResponse() {
        val response = GlobalExceptionHandler()
            .handleNotFound(NotificationNotFoundException("notice not found: id=1"))

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(404, response.body?.status)
        assertEquals("NOTIFICATION_NOT_FOUND", response.body?.code)
        assertEquals("notification not found", response.body?.message)
    }

    @Test
    fun invalidRequestReturnsStableErrorResponse() {
        val response = GlobalExceptionHandler()
            .handleInvalidRequest(IllegalArgumentException("page must be greater than or equal to 0"))

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals(400, response.body?.status)
        assertEquals("INVALID_REQUEST", response.body?.code)
        assertEquals("invalid request", response.body?.message)
    }

    @Test
    fun unhandledExceptionReturnsStableErrorResponse() {
        val response = GlobalExceptionHandler()
            .handleUnhandledException(RuntimeException("database connection failed"))

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.statusCode)
        assertEquals(500, response.body?.status)
        assertEquals("INTERNAL_SERVER_ERROR", response.body?.code)
        assertEquals("internal server error", response.body?.message)
    }

    private fun noticeRequest(category: String): CreateNoticeRequest =
        CreateNoticeRequest(
            title = "title",
            content = "content",
            category = category,
            author = "관리자",
            isPinned = true,
            attachmentIds = listOf("doc_1", "doc_2"),
        )

    private fun answerRequest(questionId: Long, content: String = "answer"): AnswerQuestionRequest =
        AnswerQuestionRequest(questionId = questionId, content = content, answeredBy = "admin")

    private class RecordingNotificationPort(
        private val failure: RuntimeException? = null,
    ) : NotificationPort {
        var created: CreateNoticeCommand? = null
        var updated: UpdateNoticeCommand? = null
        var deletedId: Long? = null
        var answered: AnswerQuestionCommand? = null

        override fun createNotice(command: CreateNoticeCommand): NoticeDetailResult {
            created = command
            return NoticeDetailResult(1L, command.title, command.content, command.author, 0, CREATED_AT, CREATED_AT)
        }

        override fun updateNotice(command: UpdateNoticeCommand): NoticeDetailResult {
            failure?.let { throw it }
            updated = command
            return NoticeDetailResult(command.noticeId, "title", "content", "관리자", 0, CREATED_AT, UPDATED_AT)
        }

        override fun deleteNotice(id: Long) {
            failure?.let { throw it }
            deletedId = id
        }

        override fun answerQuestion(command: AnswerQuestionCommand): FaqDetailResult {
            failure?.let { throw it }
            answered = command
            return FaqDetailResult(
                faqId = command.questionId,
                category = "입학 문의",
                question = "question",
                answer = command.content,
                viewCount = 0,
                createdAt = CREATED_AT,
                updatedAt = ANSWERED_AT,
                answeredAt = ANSWERED_AT,
            )
        }

        override fun getNotices(command: ReadNotificationPageCommand) = error("unused")
        override fun getNotice(id: Long) = error("unused")
        override fun getFaqs(command: ReadFaqPageCommand) = error("unused")
        override fun getFaq(id: Long) = error("unused")
        override fun getRecruitmentGuideline() = error("unused")
    }

    private companion object {
        val CREATED_AT: LocalDateTime = LocalDateTime.parse("2026-09-11T09:00:00")
        val ANSWERED_AT: LocalDateTime = LocalDateTime.parse("2026-09-12T10:00:00")
        val UPDATED_AT: LocalDateTime = LocalDateTime.parse("2026-09-14T10:00:00")
    }
}
