package hs.kr.entrydsm.notification.adapterin.grpc

import hs.kr.entrydsm.notification.application.port.`in`.CreateNoticeUseCase
import hs.kr.entrydsm.notification.application.port.`in`.command.CreateNoticeCommand
import hs.kr.entrydsm.notification.application.port.`in`.result.NoticeDetailResult
import hs.kr.entrydsm.notification.domain.model.NoticeCategory
import hs.kr.entrydsm.notification.grpc.CreateNoticeRequest
import hs.kr.entrydsm.notification.grpc.NoticeCategory as GrpcNoticeCategory
import hs.kr.entrydsm.notification.grpc.NoticeResponse
import hs.kr.entrydsm.notification.grpc.NotificationServiceGrpc
import io.grpc.Status
import io.grpc.stub.StreamObserver
import java.time.ZoneOffset
import org.springframework.stereotype.Component

/**
 * 공지사항 등록 RPC 입니다. admin 이 이 계약을 통해 등록을 위임합니다.
 */
@Component
class NotificationGrpcService(
    private val createNoticeUseCase: CreateNoticeUseCase,
) : NotificationServiceGrpc.NotificationServiceImplBase() {

    override fun createNotice(
        request: CreateNoticeRequest,
        responseObserver: StreamObserver<NoticeResponse>,
    ) = responseObserver.respond {
        createNoticeUseCase.createNotice(
            CreateNoticeCommand(
                title = request.title,
                content = request.content,
                category = request.category.toDomain(),
                author = request.author,
            ),
        )
    }

    private fun GrpcNoticeCategory.toDomain(): NoticeCategory = when (this) {
        GrpcNoticeCategory.NOTICE_CATEGORY_ADMISSION_NOTICE -> NoticeCategory.ADMISSION_NOTICE
        GrpcNoticeCategory.NOTICE_CATEGORY_PROSPECTIVE_STUDENT -> NoticeCategory.PROSPECTIVE_STUDENT
        else -> throw IllegalArgumentException("category must be specified")
    }

    private fun StreamObserver<NoticeResponse>.respond(block: () -> NoticeDetailResult) {
        try {
            onNext(block().toResponse())
            onCompleted()
        } catch (exception: Exception) {
            onError(
                when (exception) {
                    is IllegalArgumentException -> Status.INVALID_ARGUMENT
                    else -> Status.INTERNAL
                }.withCause(exception).asRuntimeException(),
            )
        }
    }

    private fun NoticeDetailResult.toResponse(): NoticeResponse =
        NoticeResponse.newBuilder()
            .setId(noticeId)
            .setTitle(title)
            .setContent(content)
            .setCategory(
                when (category) {
                    NoticeCategory.ADMISSION_NOTICE -> GrpcNoticeCategory.NOTICE_CATEGORY_ADMISSION_NOTICE
                    NoticeCategory.PROSPECTIVE_STUDENT -> GrpcNoticeCategory.NOTICE_CATEGORY_PROSPECTIVE_STUDENT
                },
            )
            .setAuthor(author)
            .setViewCount(viewCount)
            .setCreatedAtEpochMillis(createdAt.toInstant(ZoneOffset.UTC).toEpochMilli())
            .setUpdatedAtEpochMillis(updatedAt.toInstant(ZoneOffset.UTC).toEpochMilli())
            .build()
}
