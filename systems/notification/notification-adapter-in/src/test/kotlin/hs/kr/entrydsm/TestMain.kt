package hs.kr.entrydsm.notification.adapterin

import hs.kr.entrydsm.notification.adapterin.grpc.NotificationGrpcService
import hs.kr.entrydsm.notification.adapterin.web.exception.GlobalExceptionHandler
import hs.kr.entrydsm.notification.application.exception.NotificationNotFoundException
import hs.kr.entrydsm.notification.application.port.`in`.NotificationPort
import hs.kr.entrydsm.notification.application.port.`in`.command.CreateNoticeCommand
import hs.kr.entrydsm.notification.application.port.`in`.command.ReadFaqPageCommand
import hs.kr.entrydsm.notification.application.port.`in`.command.ReadNotificationPageCommand
import hs.kr.entrydsm.notification.application.port.`in`.result.NoticeDetailResult
import hs.kr.entrydsm.notification.domain.model.NoticeCategory
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
        val observer = RecordingObserver()

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

    @Test
    fun grpcCreateNoticeRejectsUnknownCategory() {
        val port = RecordingNotificationPort()
        val observer = RecordingObserver()

        NotificationGrpcService(port).createNotice(noticeRequest(category = "Admissions Notice"), observer)

        assertNull(port.created)
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

    private class RecordingNotificationPort : NotificationPort {
        var created: CreateNoticeCommand? = null

        override fun createNotice(command: CreateNoticeCommand): NoticeDetailResult {
            created = command
            return NoticeDetailResult(1L, command.title, command.content, command.author, 0, CREATED_AT, CREATED_AT)
        }

        override fun getNotices(command: ReadNotificationPageCommand) = error("unused")
        override fun getNotice(id: Long) = error("unused")
        override fun getFaqs(command: ReadFaqPageCommand) = error("unused")
        override fun getFaq(id: Long) = error("unused")
        override fun getRecruitmentGuideline() = error("unused")
    }

    private class RecordingObserver : StreamObserver<CreateNoticeResponse> {
        var value: CreateNoticeResponse? = null
        var error: Throwable? = null

        override fun onNext(value: CreateNoticeResponse) {
            this.value = value
        }

        override fun onError(t: Throwable) {
            error = t
        }

        override fun onCompleted() = Unit
    }

    private companion object {
        val CREATED_AT: LocalDateTime = LocalDateTime.parse("2026-09-11T09:00:00")
    }
}
