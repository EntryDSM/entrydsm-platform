package hs.kr.entrydsm.notification.adapterin.grpc

import hs.kr.entrydsm.notification.application.port.`in`.CreateNoticeUseCase
import hs.kr.entrydsm.notification.application.port.`in`.command.CreateNoticeCommand
import hs.kr.entrydsm.notification.application.port.`in`.result.NoticeDetailResult
import hs.kr.entrydsm.notification.domain.model.NoticeCategory
import hs.kr.entrydsm.notification.grpc.CreateNoticeRequest
import hs.kr.entrydsm.notification.grpc.NoticeCategory as GrpcNoticeCategory
import hs.kr.entrydsm.notification.grpc.NotificationServiceGrpc
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import io.grpc.Server
import io.grpc.ServerBuilder
import io.grpc.Status
import io.grpc.StatusRuntimeException
import java.time.LocalDateTime
import java.time.ZoneOffset
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

class NotificationGrpcServiceTest {
    private lateinit var useCase: FakeCreateNoticeUseCase
    private lateinit var server: Server
    private lateinit var channel: ManagedChannel
    private lateinit var stub: NotificationServiceGrpc.NotificationServiceBlockingStub

    @Before
    fun setUp() {
        useCase = FakeCreateNoticeUseCase()
        server = ServerBuilder.forPort(0).addService(NotificationGrpcService(useCase)).build().start()
        channel = ManagedChannelBuilder.forAddress("127.0.0.1", server.port).usePlaintext().build()
        stub = NotificationServiceGrpc.newBlockingStub(channel)
    }

    @After
    fun tearDown() {
        channel.shutdownNow()
        server.shutdownNow()
    }

    @Test
    fun servesCreateNoticeContract() {
        val response = stub.createNotice(
            CreateNoticeRequest.newBuilder()
                .setTitle("2027 입학 전형 안내")
                .setContent("본문")
                .setCategory(GrpcNoticeCategory.NOTICE_CATEGORY_PROSPECTIVE_STUDENT)
                .setAuthor("admin")
                .build(),
        )

        assertEquals(NOTICE_ID, response.id)
        assertEquals("2027 입학 전형 안내", response.title)
        assertEquals("본문", response.content)
        assertEquals(GrpcNoticeCategory.NOTICE_CATEGORY_PROSPECTIVE_STUDENT, response.category)
        assertEquals("admin", response.author)
        assertEquals(0, response.viewCount)
        assertEquals(CREATED_AT.toInstant(ZoneOffset.UTC).toEpochMilli(), response.createdAtEpochMillis)
        assertEquals(NoticeCategory.PROSPECTIVE_STUDENT, useCase.lastCommand?.category)
        assertEquals("admin", useCase.lastCommand?.author)
    }

    @Test
    fun mapsInvalidRequestsToInvalidArgument() {
        val unspecifiedCategory = assertThrows(StatusRuntimeException::class.java) {
            stub.createNotice(
                CreateNoticeRequest.newBuilder()
                    .setTitle("제목")
                    .setContent("본문")
                    .setAuthor("admin")
                    .build(),
            )
        }
        val blankTitle = assertThrows(StatusRuntimeException::class.java) {
            stub.createNotice(
                CreateNoticeRequest.newBuilder()
                    .setContent("본문")
                    .setCategory(GrpcNoticeCategory.NOTICE_CATEGORY_ADMISSION_NOTICE)
                    .setAuthor("admin")
                    .build(),
            )
        }

        assertEquals(Status.Code.INVALID_ARGUMENT, unspecifiedCategory.status.code)
        assertEquals(Status.Code.INVALID_ARGUMENT, blankTitle.status.code)
    }

    private class FakeCreateNoticeUseCase : CreateNoticeUseCase {
        var lastCommand: CreateNoticeCommand? = null

        override fun createNotice(command: CreateNoticeCommand): NoticeDetailResult {
            lastCommand = command
            return NoticeDetailResult(
                noticeId = NOTICE_ID,
                title = command.title,
                content = command.content,
                category = command.category,
                author = command.author,
                viewCount = 0,
                createdAt = CREATED_AT,
                updatedAt = CREATED_AT,
            )
        }
    }

    private companion object {
        const val NOTICE_ID = 7L
        val CREATED_AT: LocalDateTime = LocalDateTime.parse("2026-09-12T00:00:00")
    }
}
