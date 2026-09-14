package hs.kr.entrydsm.notification.adapterin.grpc

import hs.kr.entrydsm.notification.application.exception.NotificationNotFoundException
import hs.kr.entrydsm.notification.application.port.`in`.NotificationPort
import hs.kr.entrydsm.notification.application.port.`in`.command.AnswerQuestionCommand
import hs.kr.entrydsm.notification.application.port.`in`.command.CreateNoticeCommand
import hs.kr.entrydsm.notification.application.port.`in`.command.UpdateNoticeCommand
import hs.kr.entrydsm.notification.domain.model.NoticeCategory
import hs.kr.entrydsm.notification.grpc.AnswerQuestionRequest
import hs.kr.entrydsm.notification.grpc.AnswerQuestionResponse
import hs.kr.entrydsm.notification.grpc.CreateNoticeRequest
import hs.kr.entrydsm.notification.grpc.CreateNoticeResponse
import hs.kr.entrydsm.notification.grpc.DeleteNoticeRequest
import hs.kr.entrydsm.notification.grpc.DeleteNoticeResponse
import hs.kr.entrydsm.notification.grpc.NotificationServiceGrpc
import hs.kr.entrydsm.notification.grpc.UpdateNoticeRequest
import hs.kr.entrydsm.notification.grpc.UpdateNoticeResponse
import io.grpc.Status
import io.grpc.stub.StreamObserver
import java.time.LocalDateTime
import java.time.ZoneOffset
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
                    .setCreatedAtEpochMillis(notice.createdAt.toEpochMillis())
                    .build(),
            )
            responseObserver.onCompleted()
        } catch (exception: Exception) {
            responseObserver.onError(exception.toStatusException())
        }
    }

    override fun updateNotice(
        request: UpdateNoticeRequest,
        responseObserver: StreamObserver<UpdateNoticeResponse>,
    ) {
        try {
            val notice = notificationPort.updateNotice(
                UpdateNoticeCommand(
                    noticeId = request.noticeId,
                    title = if (request.hasTitle()) request.title else null,
                    content = if (request.hasContent()) request.content else null,
                    category = if (request.hasCategory()) NoticeCategory.from(request.category) else null,
                    isPinned = if (request.hasIsPinned()) request.isPinned else null,
                    attachmentIds = if (request.hasAttachmentIds()) request.attachmentIds.valuesList else null,
                ),
            )
            responseObserver.onNext(
                UpdateNoticeResponse.newBuilder()
                    .setNoticeId(notice.noticeId)
                    .setUpdatedAtEpochMillis(notice.updatedAt.toEpochMillis())
                    .build(),
            )
            responseObserver.onCompleted()
        } catch (exception: Exception) {
            responseObserver.onError(exception.toStatusException())
        }
    }

    override fun deleteNotice(
        request: DeleteNoticeRequest,
        responseObserver: StreamObserver<DeleteNoticeResponse>,
    ) {
        try {
            notificationPort.deleteNotice(request.noticeId)
            responseObserver.onNext(DeleteNoticeResponse.getDefaultInstance())
            responseObserver.onCompleted()
        } catch (exception: Exception) {
            responseObserver.onError(exception.toStatusException())
        }
    }

    override fun answerQuestion(
        request: AnswerQuestionRequest,
        responseObserver: StreamObserver<AnswerQuestionResponse>,
    ) {
        try {
            val faq = notificationPort.answerQuestion(
                AnswerQuestionCommand(
                    questionId = request.questionId,
                    content = request.content,
                    answeredBy = request.answeredBy,
                ),
            )
            responseObserver.onNext(
                AnswerQuestionResponse.newBuilder()
                    .setQuestionId(faq.faqId)
                    .setAnsweredAtEpochMillis((faq.answeredAt ?: faq.updatedAt).toEpochMillis())
                    .build(),
            )
            responseObserver.onCompleted()
        } catch (exception: Exception) {
            responseObserver.onError(exception.toStatusException())
        }
    }

    /** 컨테이너 기본 시간대에 기대지 않는다. application gRPC 와 같은 규칙이다. */
    private fun LocalDateTime.toEpochMillis(): Long =
        toInstant(ZoneOffset.UTC).toEpochMilli()

    private fun Exception.toStatusException() =
        when (this) {
            is IllegalArgumentException -> Status.INVALID_ARGUMENT.withDescription(message)
            is NotificationNotFoundException -> Status.NOT_FOUND.withDescription(message)
            // 설명은 호출자에게 그대로 전달된다. 저장소 예외 메시지를 싣지 않는다.
            else -> Status.INTERNAL.withDescription(INTERNAL_ERROR_DESCRIPTION)
        }.withCause(this).asRuntimeException()

    private companion object {
        const val INTERNAL_ERROR_DESCRIPTION = "internal server error"
    }
}
