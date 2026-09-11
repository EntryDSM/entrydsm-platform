package hs.kr.entrydsm.notification.adapterin.grpc

import hs.kr.entrydsm.notification.application.port.`in`.NotificationPort
import hs.kr.entrydsm.notification.application.port.`in`.command.CreateNoticeCommand
import hs.kr.entrydsm.notification.domain.model.NoticeCategory
import hs.kr.entrydsm.notification.grpc.CreateNoticeRequest
import hs.kr.entrydsm.notification.grpc.CreateNoticeResponse
import hs.kr.entrydsm.notification.grpc.NotificationServiceGrpc
import io.grpc.Status
import io.grpc.stub.StreamObserver
import java.time.ZoneId
import org.springframework.stereotype.Component

@Component
class NotificationGrpcService(
    private val notificationPort: NotificationPort,
) : NotificationServiceGrpc.NotificationServiceImplBase() {
    override fun createNotice(
        request: CreateNoticeRequest,
        responseObserver: StreamObserver<CreateNoticeResponse>,
    ) {
        try {
            val notice = notificationPort.createNotice(
                CreateNoticeCommand(
                    title = request.title,
                    content = request.content,
                    category = NoticeCategory.from(request.category),
                    author = request.author,
                    isPinned = request.isPinned,
                    attachmentIds = request.attachmentIdsList,
                ),
            )
            responseObserver.onNext(
                CreateNoticeResponse.newBuilder()
                    .setNoticeId(notice.noticeId)
                    .setCreatedAtEpochMillis(notice.createdAt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
                    .build(),
            )
            responseObserver.onCompleted()
        } catch (exception: Exception) {
            responseObserver.onError(
                when (exception) {
                    is IllegalArgumentException -> Status.INVALID_ARGUMENT
                    else -> Status.INTERNAL
                }.withDescription(exception.message).withCause(exception).asRuntimeException(),
            )
        }
    }
}
