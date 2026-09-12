package hs.kr.entrydsm.admin.adapterout.grpc

import hs.kr.entrydsm.admin.domain.model.Notice
import hs.kr.entrydsm.admin.domain.port.out.NoticeRepository
import hs.kr.entrydsm.notification.grpc.CreateNoticeRequest
import hs.kr.entrydsm.notification.grpc.NotificationServiceGrpc
import io.grpc.StatusRuntimeException
import java.time.Instant
import java.util.concurrent.TimeUnit
import org.springframework.stereotype.Component

/**
 * 공지는 notification 이 소유하므로 등록 요청을 gRPC 로 넘깁니다.
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

    private companion object {
        /** 공지 목록·상세에 보이는 작성자. 관리자 개인이 아니라 학교 명의로 게시한다. */
        const val NOTICE_AUTHOR = "관리자"
    }
}
