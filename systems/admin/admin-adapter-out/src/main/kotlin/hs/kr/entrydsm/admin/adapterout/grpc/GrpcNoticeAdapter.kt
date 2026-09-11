package hs.kr.entrydsm.admin.adapterout.grpc

import hs.kr.entrydsm.admin.domain.enum.ErrorCode
import hs.kr.entrydsm.admin.domain.exception.AdminDomainException
import hs.kr.entrydsm.admin.domain.model.Notice
import hs.kr.entrydsm.admin.domain.port.out.NoticeRepository
import hs.kr.entrydsm.notification.grpc.CreateNoticeRequest
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
 * 공지는 notification 이 소유하므로 등록 요청을 gRPC 로 넘깁니다.
 */
@Component
class GrpcNoticeAdapter(
    @Value("\${notification.grpc.host}") host: String,
    @Value("\${notification.grpc.port}") port: Int,
    @Value("\${notification.grpc.deadline-ms}") private val deadlineMs: Long,
) : NoticeRepository, DisposableBean {
    private val channel: ManagedChannel = ManagedChannelBuilder.forAddress(host, port)
        .usePlaintext()
        .build()
    private val stub = NotificationServiceGrpc.newBlockingStub(channel)

    override fun save(notice: Notice): Notice {
        val response = try {
            stub.withDeadlineAfter(deadlineMs, TimeUnit.MILLISECONDS).createNotice(
                CreateNoticeRequest.newBuilder()
                    .setTitle(notice.title)
                    .setContent(notice.content)
                    .setCategory(notice.division)
                    .setAuthor(NOTICE_AUTHOR)
                    .setIsPinned(notice.isPinned)
                    .addAllAttachmentIds(notice.attachmentIds)
                    .build(),
            )
        } catch (exception: StatusRuntimeException) {
            throw AdminDomainException(
                when (exception.status.code) {
                    Status.Code.INVALID_ARGUMENT -> ErrorCode.INVALID_REQUEST_BODY
                    Status.Code.UNAVAILABLE, Status.Code.DEADLINE_EXCEEDED -> ErrorCode.NOTIFICATION_SERVICE_UNAVAILABLE
                    else -> ErrorCode.INTERNAL_SERVER_ERROR
                },
                exception,
            )
        }
        return notice.copy(
            id = response.noticeId,
            createdAt = Instant.ofEpochMilli(response.createdAtEpochMillis),
        )
    }

    override fun destroy() {
        channel.shutdown().awaitTermination(5, TimeUnit.SECONDS)
    }

    private companion object {
        /** 공지 목록·상세에 보이는 작성자. 관리자 개인이 아니라 학교 명의로 게시한다. */
        const val NOTICE_AUTHOR = "관리자"
    }
}
