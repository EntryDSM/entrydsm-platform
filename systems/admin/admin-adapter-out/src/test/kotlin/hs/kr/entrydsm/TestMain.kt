package hs.kr.entrydsm.admin.adapterout

import com.fasterxml.jackson.databind.ObjectMapper
import hs.kr.entrydsm.admin.adapterout.distance.KakaoDistanceAdapter
import hs.kr.entrydsm.admin.adapterout.grpc.GrpcNoticeAdapter
import hs.kr.entrydsm.admin.adapterout.grpc.NotificationGrpcChannel
import hs.kr.entrydsm.admin.domain.command.UpdateNoticeCommand
import hs.kr.entrydsm.admin.domain.enum.ErrorCode
import hs.kr.entrydsm.admin.domain.exception.AdminDomainException
import hs.kr.entrydsm.notification.grpc.DeleteNoticeRequest
import hs.kr.entrydsm.notification.grpc.DeleteNoticeResponse
import hs.kr.entrydsm.notification.grpc.NotificationServiceGrpc
import hs.kr.entrydsm.notification.grpc.UpdateNoticeRequest
import hs.kr.entrydsm.notification.grpc.UpdateNoticeResponse
import io.grpc.ServerBuilder
import io.grpc.Status
import io.grpc.stub.StreamObserver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdminAdapterOutModuleTest {
    @Test
    fun moduleLoads() {
        assertTrue(true)
    }

    @Test
    fun kakaoDistanceReadsCoordinatesAndMeterValue() {
        val adapter = KakaoDistanceAdapter(ObjectMapper(), "key", "https://local.test", "https://directions.test", "학교", 1000)

        val distance = adapter.parseDistance(
            """{"routes":[{"result_code":0,"summary":{"distance":1234}}]}""",
        )
        val coordinates = adapter.parseCoordinates("""{"documents":[{"x":"127.1","y":"36.3"}]}""")

        assertEquals(1234L, distance)
        assertEquals(KakaoDistanceAdapter.Coordinates(127.1, 36.3), coordinates)
    }

    @Test
    fun kakaoFailureDoesNotExposeApiKey() {
        val adapter = KakaoDistanceAdapter(ObjectMapper(), "super-secret", "::", "::", "학교", 1)

        val exception = runCatching { adapter.distanceFromSchool("집") }.exceptionOrNull()

        assertTrue(exception is AdminDomainException)
        assertFalse(exception?.message.orEmpty().contains("super-secret"))
    }

    /** false 와 빈 첨부 목록도 보낸 값이라 전송 뒤에도 필드가 살아 있어야 한다. */
    @Test
    fun grpcUpdateNoticeSendsProvidedFieldsIncludingFalseAndEmptyList() {
        val notification = FakeNotificationService()

        withAdapter(notification) { adapter ->
            adapter.update(
                UpdateNoticeCommand(noticeId = 3L, title = "new title", isPinned = false, attachmentIds = emptyList()),
            )
        }

        val request = notification.updated!!
        assertEquals(3L, request.noticeId)
        assertEquals("new title", request.title)
        assertTrue(request.hasIsPinned())
        assertFalse(request.isPinned)
        assertTrue(request.hasAttachmentIds())
        assertEquals(0, request.attachmentIds.valuesCount)
    }

    /** null 인 필드를 요청에 넣으면 notification 이 기본값으로 덮어쓴다. */
    @Test
    fun grpcUpdateNoticeLeavesNullFieldsUnset() {
        val notification = FakeNotificationService()

        withAdapter(notification) { adapter ->
            adapter.update(UpdateNoticeCommand(noticeId = 3L, division = "PROSPECTIVE_STUDENT"))
        }

        val request = notification.updated!!
        assertEquals("PROSPECTIVE_STUDENT", request.category)
        assertFalse(request.hasTitle())
        assertFalse(request.hasContent())
        assertFalse(request.hasIsPinned())
        assertFalse(request.hasAttachmentIds())
    }

    @Test
    fun grpcDeleteNoticeMapsNotFoundToNoticeNotFound() {
        val notification = FakeNotificationService(failure = Status.NOT_FOUND)

        val exception = withAdapter(notification) { adapter ->
            runCatching { adapter.deleteById(404L) }.exceptionOrNull()
        }

        assertEquals(404L, notification.deletedId)
        assertTrue(exception is AdminDomainException)
        assertEquals(ErrorCode.NOTICE_NOT_FOUND, (exception as AdminDomainException).errorCode)
    }

    /** 실제 직렬화를 거치도록 로컬 포트에 가짜 notification 서버를 띄운다. */
    private fun <T> withAdapter(notification: FakeNotificationService, block: (GrpcNoticeAdapter) -> T): T {
        val server = ServerBuilder.forPort(0).addService(notification).build().start()
        val channel = NotificationGrpcChannel("localhost", server.port, 3000)
        return try {
            block(GrpcNoticeAdapter(channel))
        } finally {
            channel.destroy()
            server.shutdownNow()
        }
    }

    private class FakeNotificationService(
        private val failure: Status? = null,
    ) : NotificationServiceGrpc.NotificationServiceImplBase() {
        @Volatile
        var updated: UpdateNoticeRequest? = null

        @Volatile
        var deletedId: Long? = null

        override fun updateNotice(
            request: UpdateNoticeRequest,
            responseObserver: StreamObserver<UpdateNoticeResponse>,
        ) {
            updated = request
            respond(responseObserver, UpdateNoticeResponse.newBuilder().setNoticeId(request.noticeId).build())
        }

        override fun deleteNotice(
            request: DeleteNoticeRequest,
            responseObserver: StreamObserver<DeleteNoticeResponse>,
        ) {
            deletedId = request.noticeId
            respond(responseObserver, DeleteNoticeResponse.getDefaultInstance())
        }

        private fun <T> respond(observer: StreamObserver<T>, response: T) {
            if (failure != null) {
                observer.onError(failure.asRuntimeException())
                return
            }
            observer.onNext(response)
            observer.onCompleted()
        }
    }
}
