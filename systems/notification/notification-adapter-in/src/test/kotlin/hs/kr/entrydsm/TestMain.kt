package hs.kr.entrydsm.notification.adapterin

import hs.kr.entrydsm.notification.adapterin.grpc.NotificationGrpcService
import hs.kr.entrydsm.notification.adapterin.web.exception.GlobalExceptionHandler
import hs.kr.entrydsm.notification.application.exception.NotificationNotFoundException
import hs.kr.entrydsm.notification.application.port.`in`.NotificationPort
import hs.kr.entrydsm.notification.application.port.`in`.command.AnswerQuestionCommand
import hs.kr.entrydsm.notification.application.port.`in`.command.CreateNoticeCommand
import hs.kr.entrydsm.notification.application.port.`in`.command.ReadFaqPageCommand
import hs.kr.entrydsm.notification.application.port.`in`.command.ReadNotificationPageCommand
import hs.kr.entrydsm.notification.application.port.`in`.result.FaqDetailResult
import hs.kr.entrydsm.notification.application.port.`in`.result.NoticeDetailResult
import hs.kr.entrydsm.notification.domain.model.NoticeCategory
import hs.kr.entrydsm.notification.grpc.AnswerQuestionRequest
import hs.kr.entrydsm.notification.grpc.AnswerQuestionResponse
import hs.kr.entrydsm.notification.grpc.CreateNoticeRequest
import hs.kr.entrydsm.notification.grpc.CreateNoticeResponse
import io.grpc.Status
import io.grpc.stub.StreamObserver
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.springframework.http.HttpStatus

class NotificationAdapterInModuleTest {
    @Test
    fun moduleLoads() {
        assertTrue(true)
    }

    @Test
    fun grpcCreateNoticePassesAllFieldsToPort() {
        val port = RecordingNotificationPort()
        val observer = RecordingObserver<CreateNoticeResponse>()

        NotificationGrpcService(port).createNotice(noticeRequest(category = "PROSPECTIVE_STUDENT"), observer)

        val created = port.created!!
        assertEquals("title", created.title)
        assertEquals("content", created.content)
        assertEquals(NoticeCategory.PROSPECTIVE_STUDENT, created.category)
        assertEquals("관리자", created.author)
        assertTrue(created.isPinned)
        assertEquals(listOf("doc_1", "doc_2"), created.attachmentIds)
        assertEquals(1L, observer.value?.noticeId)
        assertEquals(
            CREATED_AT.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
            observer.value?.createdAtEpochMillis,
        )
    }

    /** Notion 명세가 예시로 쓰는 영문 이름도 같은 분류로 치환해 받는다. */
    @Test
    fun grpcCreateNoticeAcceptsSpecCategoryNames() {
        val port = RecordingNotificationPort()

        NotificationGrpcService(port)
            .createNotice(noticeRequest(category = "Admissions Notice"), RecordingObserver())
        assertEquals(NoticeCategory.ADMISSION_NOTICE, port.created?.category)

        NotificationGrpcService(port)
            .createNotice(noticeRequest(category = "Prospective Students Notice"), RecordingObserver())
        assertEquals(NoticeCategory.PROSPECTIVE_STUDENT, port.created?.category)
    }

    @Test
    fun grpcCreateNoticeAcceptsKoreanCategoryNames() {
        val port = RecordingNotificationPort()

        NotificationGrpcService(port)
            .createNotice(noticeRequest(category = "예비 신입생 안내"), RecordingObserver())

        assertEquals(NoticeCategory.PROSPECTIVE_STUDENT, port.created?.category)
    }

    @Test
    fun grpcCreateNoticeRejectsUnknownCategory() {
        val port = RecordingNotificationPort()
        val observer = RecordingObserver<CreateNoticeResponse>()

        NotificationGrpcService(port).createNotice(noticeRequest(category = "Graduation Notice"), observer)

        assertNull(port.created)
        assertEquals(Status.Code.INVALID_ARGUMENT, Status.fromThrowable(observer.error).code)
    }

    @Test
    fun grpcAnswerQuestionPassesAllFieldsToPort() {
        val port = RecordingNotificationPort()
        val observer = RecordingObserver<AnswerQuestionResponse>()

        NotificationGrpcService(port).answerQuestion(answerRequest(questionId = 7L), observer)

        val answered = port.answered!!
        assertEquals(7L, answered.questionId)
        assertEquals("answer", answered.content)
        assertEquals("admin", answered.answeredBy)
        assertEquals(7L, observer.value?.questionId)
        assertEquals(
            ANSWERED_AT.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
            observer.value?.answeredAtEpochMillis,
        )
    }

    @Test
    fun grpcAnswerQuestionReportsMissingQuestionAsNotFound() {
        val port = RecordingNotificationPort(questionExists = false)
        val observer = RecordingObserver<AnswerQuestionResponse>()

        NotificationGrpcService(port).answerQuestion(answerRequest(questionId = 404L), observer)

        assertNull(observer.value)
        assertEquals(Status.Code.NOT_FOUND, Status.fromThrowable(observer.error).code)
    }

    @Test
    fun grpcAnswerQuestionRejectsBlankContent() {
        val port = RecordingNotificationPort()
        val observer = RecordingObserver<AnswerQuestionResponse>()

        NotificationGrpcService(port)
            .answerQuestion(answerRequest(questionId = 7L, content = " "), observer)

        assertNull(port.answered)
        assertEquals(Status.Code.INVALID_ARGUMENT, Status.fromThrowable(observer.error).code)
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
        CreateNoticeRequest.newBuilder()
            .setTitle("title")
            .setContent("content")
            .setCategory(category)
            .setAuthor("관리자")
            .setIsPinned(true)
            .addAllAttachmentIds(listOf("doc_1", "doc_2"))
            .build()

    private fun answerRequest(questionId: Long, content: String = "answer"): AnswerQuestionRequest =
        AnswerQuestionRequest.newBuilder()
            .setQuestionId(questionId)
            .setContent(content)
            .setAnsweredBy("admin")
            .build()

    private class RecordingNotificationPort(
        private val questionExists: Boolean = true,
    ) : NotificationPort {
        var created: CreateNoticeCommand? = null
        var answered: AnswerQuestionCommand? = null

        override fun createNotice(command: CreateNoticeCommand): NoticeDetailResult {
            created = command
            return NoticeDetailResult(1L, command.title, command.content, command.author, 0, CREATED_AT, CREATED_AT)
        }

        override fun answerQuestion(command: AnswerQuestionCommand): FaqDetailResult {
            if (!questionExists) {
                throw NotificationNotFoundException("faq not found: id=${command.questionId}")
            }
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

    private class RecordingObserver<T> : StreamObserver<T> {
        var value: T? = null
        var error: Throwable? = null

        override fun onNext(value: T) {
            this.value = value
        }

        override fun onError(t: Throwable) {
            error = t
        }

        override fun onCompleted() = Unit
    }

    private companion object {
        val CREATED_AT: LocalDateTime = LocalDateTime.parse("2026-09-11T09:00:00")
        val ANSWERED_AT: LocalDateTime = LocalDateTime.parse("2026-09-12T10:00:00")
    }
}
