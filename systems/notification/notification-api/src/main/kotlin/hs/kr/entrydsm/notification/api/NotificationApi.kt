package hs.kr.entrydsm.notification.api

import java.time.Instant

/**
 * notification 모듈이 다른 모듈에 공개하는 공지·질문 답변 API다. `notification.proto`의 `NotificationService`를 대신한다.
 *
 * 같은 프로세스 안에서 호출되므로 호출자의 트랜잭션에 합류한다. 예외는 [NotificationApiException] 으로만 알린다.
 * 그 밖의 예외(저장소 장애 등)는 변환하지 않고 그대로 전파한다.
 */
interface NotificationApi {
    /** @throws NotificationApiException.InvalidArgument 제목·본문·작성자가 비었거나 분류를 알 수 없을 때 */
    fun createNotice(request: CreateNoticeRequest): NoticeCreated

    /** @throws NotificationApiException.NotFound 공지가 없을 때 */
    fun updateNotice(request: UpdateNoticeRequest): NoticeUpdated

    /** @throws NotificationApiException.NotFound 공지가 없을 때 */
    fun deleteNotice(noticeId: Long)

    /** 관리자가 등록한 답변을 질문에 붙인다. @throws NotificationApiException.NotFound 질문이 없을 때 */
    fun answerQuestion(request: AnswerQuestionRequest): QuestionAnswered
}

data class CreateNoticeRequest(
    val title: String,
    val content: String,
    /**
     * 공지 분류. 분류 이름(`ADMISSION_NOTICE`, `PROSPECTIVE_STUDENT`) 외에
     * 한글 이름(입학 공지사항, 예비 신입생 안내)과 명세의 영문 이름(Admissions Notice, Prospective Students Notice)도 받는다.
     */
    val category: String,
    val author: String,
    val isPinned: Boolean,
    val attachmentIds: List<String>,
)

/** 값이 있는 필드만 바꾸고, null 인 필드는 기존 값을 유지한다. */
data class UpdateNoticeRequest(
    val noticeId: Long,
    val title: String? = null,
    val content: String? = null,
    /** [CreateNoticeRequest.category] 와 같은 이름을 받는다. */
    val category: String? = null,
    val isPinned: Boolean? = null,
    /** null 이면 첨부를 그대로 두고, 목록이 있으면 그 목록으로 교체한다. 빈 목록이면 첨부를 모두 뗀다. */
    val attachmentIds: List<String>? = null,
)

data class NoticeCreated(
    val noticeId: Long,
    val createdAt: Instant,
)

data class NoticeUpdated(
    val noticeId: Long,
    val updatedAt: Instant,
)

data class AnswerQuestionRequest(
    val questionId: Long,
    val content: String,
    val answeredBy: String,
)

/** 답변은 질문에 직접 붙으므로 따로 식별자가 없다. */
data class QuestionAnswered(
    val questionId: Long,
    val answeredAt: Instant,
)

/** gRPC 상태 코드(INVALID_ARGUMENT, NOT_FOUND)를 대신한다. */
sealed class NotificationApiException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause) {
    class InvalidArgument(message: String, cause: Throwable? = null) : NotificationApiException(message, cause)

    class NotFound(message: String, cause: Throwable? = null) : NotificationApiException(message, cause)
}
