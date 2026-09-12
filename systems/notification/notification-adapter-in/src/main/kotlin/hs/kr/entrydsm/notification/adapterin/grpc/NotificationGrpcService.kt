package hs.kr.entrydsm.notification.adapterin.grpc

import hs.kr.entrydsm.notification.application.exception.NotificationNotFoundException
import hs.kr.entrydsm.notification.application.port.`in`.NotificationPort
import hs.kr.entrydsm.notification.application.port.`in`.command.AnswerQuestionCommand
import hs.kr.entrydsm.notification.application.port.`in`.command.CreateNoticeCommand
import hs.kr.entrydsm.notification.domain.model.NoticeCategory
import hs.kr.entrydsm.notification.grpc.AnswerQuestionRequest
import hs.kr.entrydsm.notification.grpc.AnswerQuestionResponse
import hs.kr.entrydsm.notification.grpc.CreateNoticeRequest
import hs.kr.entrydsm.notification.grpc.CreateNoticeResponse
import hs.kr.entrydsm.notification.grpc.NotificationServiceGrpc
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
