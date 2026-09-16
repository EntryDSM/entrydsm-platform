package hs.kr.entrydsm.admin.adapterout.notification

import hs.kr.entrydsm.admin.domain.command.UpdateNoticeCommand
import hs.kr.entrydsm.admin.domain.enum.ErrorCode
import hs.kr.entrydsm.admin.domain.model.Notice
import hs.kr.entrydsm.admin.domain.port.out.NoticeRepository
import hs.kr.entrydsm.notification.api.CreateNoticeRequest
import hs.kr.entrydsm.notification.api.NotificationApi
import hs.kr.entrydsm.notification.api.UpdateNoticeRequest
import org.springframework.stereotype.Component

/**
 * 공지는 notification 이 소유하므로 등록·수정·삭제 요청을 그 모듈의 공개 API 로 넘깁니다.
 */
@Component
class NotificationApiNoticeAdapter(
    private val notificationApi: NotificationApi,
) : NoticeRepository {
    override fun save(notice: Notice): Notice {
        val created = callNotification {
            notificationApi.createNotice(
                CreateNoticeRequest(
                    title = notice.title,
                    content = notice.content,
                    category = notice.division,
                    author = NOTICE_AUTHOR,
                    isPinned = notice.isPinned,
                    attachmentIds = notice.attachmentIds,
                ),
            )
        }
        return notice.copy(id = created.noticeId, createdAt = created.createdAt)
    }

    /** null 인 필드는 그대로 넘겨야 notification 이 기존 값을 유지한다. */
    override fun update(command: UpdateNoticeCommand) {
        callNotification(notFound = ErrorCode.NOTICE_NOT_FOUND) {
            notificationApi.updateNotice(
                UpdateNoticeRequest(
                    noticeId = command.noticeId,
                    title = command.title,
                    content = command.content,
                    category = command.division,
                    isPinned = command.isPinned,
                    attachmentIds = command.attachmentIds,
                ),
            )
        }
    }

    override fun deleteById(noticeId: Long) {
        callNotification(notFound = ErrorCode.NOTICE_NOT_FOUND) {
            notificationApi.deleteNotice(noticeId)
        }
    }

    private companion object {
        /** 공지 목록·상세에 보이는 작성자. 관리자 개인이 아니라 학교 명의로 게시한다. */
        const val NOTICE_AUTHOR = "관리자"
    }
}
