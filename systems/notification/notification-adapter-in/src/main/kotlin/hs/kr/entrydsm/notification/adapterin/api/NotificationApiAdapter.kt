package hs.kr.entrydsm.notification.adapterin.api

import hs.kr.entrydsm.notification.api.AnswerQuestionRequest
import hs.kr.entrydsm.notification.api.CreateNoticeRequest
import hs.kr.entrydsm.notification.api.NoticeCreated
import hs.kr.entrydsm.notification.api.NoticeUpdated
import hs.kr.entrydsm.notification.api.NotificationApi
import hs.kr.entrydsm.notification.api.NotificationApiException
import hs.kr.entrydsm.notification.api.QuestionAnswered
import hs.kr.entrydsm.notification.api.UpdateNoticeRequest
import hs.kr.entrydsm.notification.application.exception.NotificationNotFoundException
import hs.kr.entrydsm.notification.application.port.`in`.NotificationPort
import hs.kr.entrydsm.notification.application.port.`in`.command.AnswerQuestionCommand
import hs.kr.entrydsm.notification.application.port.`in`.command.CreateNoticeCommand
import hs.kr.entrydsm.notification.application.port.`in`.command.UpdateNoticeCommand
import hs.kr.entrydsm.notification.domain.model.NoticeCategory
import java.time.LocalDateTime
import java.time.ZoneOffset
import org.springframework.stereotype.Component

/**
 * 다른 모듈의 공지·답변 요청을 use case 로 넘긴다. gRPC 서버(`NotificationGrpcService`)가 하던 검증·변환을 그대로 한다.
 *
 * 입력 검증은 command 생성과 분류 해석에서 일어난다. 같은 프로세스에서 온 요청이라도 검증을 건너뛰지 않는다.
 */
@Component
class NotificationApiAdapter(
    private val notificationPort: NotificationPort,
) : NotificationApi {
    override fun createNotice(request: CreateNoticeRequest): NoticeCreated = translate {
        val notice = notificationPort.createNotice(
            CreateNoticeCommand(
                title = request.title,
                content = request.content,
                category = NoticeCategory.from(request.category),
                author = request.author,
                isPinned = request.isPinned,
                attachmentIds = request.attachmentIds.toList(),
            ),
        )
        NoticeCreated(noticeId = notice.noticeId, createdAt = notice.createdAt.toUtcInstant())
    }

    override fun updateNotice(request: UpdateNoticeRequest): NoticeUpdated = translate {
        val notice = notificationPort.updateNotice(
            UpdateNoticeCommand(
                noticeId = request.noticeId,
                title = request.title,
                content = request.content,
                category = request.category?.let(NoticeCategory::from),
                isPinned = request.isPinned,
                attachmentIds = request.attachmentIds?.toList(),
            ),
        )
        NoticeUpdated(noticeId = notice.noticeId, updatedAt = notice.updatedAt.toUtcInstant())
    }

    override fun deleteNotice(noticeId: Long) = translate {
        notificationPort.deleteNotice(noticeId)
    }

    override fun answerQuestion(request: AnswerQuestionRequest): QuestionAnswered = translate {
        val faq = notificationPort.answerQuestion(
            AnswerQuestionCommand(
                questionId = request.questionId,
                content = request.content,
                answeredBy = request.answeredBy,
            ),
        )
        QuestionAnswered(questionId = faq.faqId, answeredAt = (faq.answeredAt ?: faq.updatedAt).toUtcInstant())
    }

    private fun <T> translate(block: () -> T): T =
        try {
            block()
        } catch (exception: IllegalArgumentException) {
            throw NotificationApiException.InvalidArgument(exception.message ?: "invalid argument", exception)
        } catch (exception: NotificationNotFoundException) {
            throw NotificationApiException.NotFound(exception.message ?: "not found", exception)
        }

    /** 컨테이너 기본 시간대에 기대지 않는다. application 상태 API 와 같은 규칙이다. */
    private fun LocalDateTime.toUtcInstant() = toInstant(ZoneOffset.UTC)
}
