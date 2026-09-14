package hs.kr.entrydsm.admin.adapterout.grpc

import hs.kr.entrydsm.admin.domain.command.UpdateNoticeCommand
import hs.kr.entrydsm.admin.domain.enum.ErrorCode
import hs.kr.entrydsm.admin.domain.model.Notice
import hs.kr.entrydsm.admin.domain.port.out.NoticeRepository
import hs.kr.entrydsm.notification.grpc.AttachmentIds
import hs.kr.entrydsm.notification.grpc.CreateNoticeRequest
import hs.kr.entrydsm.notification.grpc.DeleteNoticeRequest
import hs.kr.entrydsm.notification.grpc.NotificationServiceGrpc
import hs.kr.entrydsm.notification.grpc.UpdateNoticeRequest
import io.grpc.StatusRuntimeException
import java.time.Instant
import java.util.concurrent.TimeUnit
import org.springframework.stereotype.Component

/**
 * 공지는 notification 이 소유하므로 등록·수정·삭제 요청을 gRPC 로 넘깁니다.
 */
@Component
class GrpcNoticeAdapter(
    private val grpc: NotificationGrpcChannel,
) : NoticeRepository {
    private val stub = NotificationServiceGrpc.newBlockingStub(grpc.channel)

    override fun save(notice: Notice): Notice {
        val response = try {
            stub.withDeadlineAfter(grpc.deadlineMs, TimeUnit.MILLISECONDS).createNotice(
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
            throw exception.toAdminException()
        }
        return notice.copy(
            id = response.noticeId,
            createdAt = Instant.ofEpochMilli(response.createdAtEpochMillis),
        )
    }

    /** null 인 필드는 요청에 넣지 않아야 notification 이 기존 값을 유지한다. */
    override fun update(command: UpdateNoticeCommand) {
        val request = UpdateNoticeRequest.newBuilder().setNoticeId(command.noticeId)
        command.title?.let { request.setTitle(it) }
        command.content?.let { request.setContent(it) }
        command.division?.let { request.setCategory(it) }
        command.isPinned?.let { request.setIsPinned(it) }
        command.attachmentIds?.let { request.setAttachmentIds(AttachmentIds.newBuilder().addAllValues(it)) }
        try {
            stub.withDeadlineAfter(grpc.deadlineMs, TimeUnit.MILLISECONDS).updateNotice(request.build())
        } catch (exception: StatusRuntimeException) {
            throw exception.toAdminException(notFound = ErrorCode.NOTICE_NOT_FOUND)
        }
    }

    override fun deleteById(noticeId: Long) {
        try {
            stub.withDeadlineAfter(grpc.deadlineMs, TimeUnit.MILLISECONDS).deleteNotice(
                DeleteNoticeRequest.newBuilder().setNoticeId(noticeId).build(),
            )
        } catch (exception: StatusRuntimeException) {
            throw exception.toAdminException(notFound = ErrorCode.NOTICE_NOT_FOUND)
        }
    }

    private companion object {
        /** 공지 목록·상세에 보이는 작성자. 관리자 개인이 아니라 학교 명의로 게시한다. */
        const val NOTICE_AUTHOR = "관리자"
    }
}
