package hs.kr.entrydsm.admin.adapterout.grpc

import hs.kr.entrydsm.admin.domain.command.CreateNoticeCommand
import hs.kr.entrydsm.admin.domain.enum.ErrorCode
import hs.kr.entrydsm.admin.domain.enum.NoticeCategory
import hs.kr.entrydsm.admin.domain.exception.AdminDomainException
import hs.kr.entrydsm.notification.grpc.CreateNoticeRequest
import hs.kr.entrydsm.notification.grpc.NoticeCategory as GrpcNoticeCategory
import hs.kr.entrydsm.notification.grpc.NoticeResponse
import hs.kr.entrydsm.notification.grpc.NotificationServiceGrpc
import io.grpc.Server
import io.grpc.ServerBuilder
import io.grpc.Status
import io.grpc.stub.StreamObserver
import java.time.Instant
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

class GrpcNoticeAdapterTest {
    private lateinit var service: FakeNotificationService
    private lateinit var server: Server
    private lateinit var adapter: GrpcNoticeAdapter

    @Before
    fun setUp() {
        service = FakeNotificationService()
        server = ServerBuilder.forPort(0).addService(service).build().start()
        adapter = GrpcNoticeAdapter("127.0.0.1", server.port, 10_000)
    }

    @After
    fun tearDown() {
        adapter.destroy()
        server.shutdownNow()
    }

    @Test
    fun delegatesNoticeCreationToNotification() {
        val notice = adapter.create(
            CreateNoticeCommand(
                title = "2027 입학 전형 안내",
                content = "본문",
                category = NoticeCategory.PROSPECTIVE_STUDENT,
                author = "admin",
            ),
        )

        assertEquals(NOTICE_ID, notice.id)
        assertEquals("2027 입학 전형 안내", notice.title)
        assertEquals("본문", notice.content)
        assertEquals(NoticeCategory.PROSPECTIVE_STUDENT, notice.category)
        assertEquals("admin", notice.author)
        assertEquals(CREATED_AT, notice.createdAt)
        assertEquals(
            GrpcNoticeCategory.NOTICE_CATEGORY_PROSPECTIVE_STUDENT,
            service.lastRequest?.category,
        )
        assertEquals("admin", service.lastRequest?.author)
    }

    @Test
    fun mapsGrpcErrors() {
        service.failWith = Status.UNAVAILABLE
        val unavailable = assertThrows(AdminDomainException::class.java) { adapter.create(command()) }

        service.failWith = Status.INVALID_ARGUMENT
        val invalid = assertThrows(AdminDomainException::class.java) { adapter.create(command()) }

        service.failWith = Status.DEADLINE_EXCEEDED
        val deadline = assertThrows(AdminDomainException::class.java) { adapter.create(command()) }

        service.failWith = Status.INTERNAL
        val internal = assertThrows(AdminDomainException::class.java) { adapter.create(command()) }

        assertEquals(ErrorCode.NOTIFICATION_SERVICE_UNAVAILABLE, unavailable.errorCode)
        assertEquals(ErrorCode.NOTIFICATION_SERVICE_UNAVAILABLE, deadline.errorCode)
        assertEquals(ErrorCode.INVALID_REQUEST_BODY, invalid.errorCode)
        assertEquals(ErrorCode.NOTICE_CREATION_FAILED, internal.errorCode)
    }

    private fun command(): CreateNoticeCommand = CreateNoticeCommand(
        title = "제목",
        content = "본문",
        category = NoticeCategory.ADMISSION_NOTICE,
        author = "admin",
    )

    private class FakeNotificationService : NotificationServiceGrpc.NotificationServiceImplBase() {
        var lastRequest: CreateNoticeRequest? = null
        var failWith: Status? = null

        override fun createNotice(
            request: CreateNoticeRequest,
            responseObserver: StreamObserver<NoticeResponse>,
        ) {
            lastRequest = request
            failWith?.let {
                responseObserver.onError(it.asRuntimeException())
                return
            }
            responseObserver.onNext(
                NoticeResponse.newBuilder()
                    .setId(NOTICE_ID)
                    .setTitle(request.title)
                    .setContent(request.content)
                    .setCategory(request.category)
                    .setAuthor(request.author)
                    .setViewCount(0)
                    .setCreatedAtEpochMillis(CREATED_AT.toEpochMilli())
                    .setUpdatedAtEpochMillis(CREATED_AT.toEpochMilli())
                    .build(),
            )
            responseObserver.onCompleted()
        }
    }

    private companion object {
        const val NOTICE_ID = 7L
        val CREATED_AT: Instant = Instant.parse("2026-09-12T00:00:00Z")
    }
}
