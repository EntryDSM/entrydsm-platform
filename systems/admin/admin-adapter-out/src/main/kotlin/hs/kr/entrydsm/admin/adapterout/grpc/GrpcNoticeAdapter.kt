package hs.kr.entrydsm.admin.adapterout.grpc

import hs.kr.entrydsm.admin.domain.command.CreateNoticeCommand
import hs.kr.entrydsm.admin.domain.enum.ErrorCode
import hs.kr.entrydsm.admin.domain.enum.NoticeCategory
import hs.kr.entrydsm.admin.domain.exception.AdminDomainException
import hs.kr.entrydsm.admin.domain.model.Notice
import hs.kr.entrydsm.admin.domain.port.out.NoticePort
import hs.kr.entrydsm.notification.grpc.CreateNoticeRequest
import hs.kr.entrydsm.notification.grpc.NoticeCategory as GrpcNoticeCategory
import hs.kr.entrydsm.notification.grpc.NoticeResponse
import hs.kr.entrydsm.notification.grpc.NotificationServiceGrpc
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import io.grpc.Status
import io.grpc.StatusRuntimeException
import java.time.Instant
import java.util.concurrent.TimeUnit
import org.springframework.beans.factory.DisposableBean
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/**
 * 공지사항 등록을 notification 시스템에 위임합니다.
 *
 * admin 이 공지를 자기 DB 에 저장하면 notification 의 공지 조회 API 에 나타나지 않습니다.
 * 공지의 소유자는 notification 이므로 등록도 그쪽에서 이뤄져야 합니다.
 */
@Component
class GrpcNoticeAdapter(
    @Value("\${notification.grpc.host}") host: String,
    @Value("\${notification.grpc.port}") port: Int,
    @Value("\${notification.grpc.deadline-ms:3000}") private val deadlineMs: Long,
) : NoticePort, DisposableBean {
    private val channel: ManagedChannel = ManagedChannelBuilder.forAddress(host, port)
        .usePlaintext()
        .build()
    private val stub = NotificationServiceGrpc.newBlockingStub(channel)

    override fun create(command: CreateNoticeCommand): Notice =
        try {
            stub.withDeadlineAfter(deadlineMs, TimeUnit.MILLISECONDS)
                .createNotice(
                    CreateNoticeRequest.newBuilder()
                        .setTitle(command.title)
                        .setContent(command.content)
                        .setCategory(command.category.toGrpc())
                        .setAuthor(command.author)
                        .build(),
                )
                .toDomain()
        } catch (exception: StatusRuntimeException) {
            throw exception.toDomainException()
        }

    override fun destroy() {
        channel.shutdown().awaitTermination(5, TimeUnit.SECONDS)
    }

    private fun NoticeCategory.toGrpc(): GrpcNoticeCategory = when (this) {
        NoticeCategory.ADMISSION_NOTICE -> GrpcNoticeCategory.NOTICE_CATEGORY_ADMISSION_NOTICE
        NoticeCategory.PROSPECTIVE_STUDENT -> GrpcNoticeCategory.NOTICE_CATEGORY_PROSPECTIVE_STUDENT
    }

    private fun NoticeResponse.toDomain(): Notice = Notice(
        id = id,
        title = title,
        content = content,
        category = when (category) {
            GrpcNoticeCategory.NOTICE_CATEGORY_ADMISSION_NOTICE -> NoticeCategory.ADMISSION_NOTICE
            GrpcNoticeCategory.NOTICE_CATEGORY_PROSPECTIVE_STUDENT -> NoticeCategory.PROSPECTIVE_STUDENT
            else -> throw AdminDomainException(ErrorCode.NOTICE_CREATION_FAILED)
        },
        author = author,
        viewCount = viewCount,
        createdAt = Instant.ofEpochMilli(createdAtEpochMillis),
        updatedAt = Instant.ofEpochMilli(updatedAtEpochMillis),
    )

    private fun StatusRuntimeException.toDomainException(): AdminDomainException = AdminDomainException(
        when (status.code) {
            Status.Code.INVALID_ARGUMENT -> ErrorCode.INVALID_REQUEST_BODY
            Status.Code.UNAVAILABLE, Status.Code.DEADLINE_EXCEEDED -> ErrorCode.NOTIFICATION_SERVICE_UNAVAILABLE
            else -> ErrorCode.NOTICE_CREATION_FAILED
        },
        this,
    )
}
