package hs.kr.entrydsm.admin.adapterout.notification

import hs.kr.entrydsm.admin.domain.enum.ErrorCode
import hs.kr.entrydsm.admin.domain.model.QuestionAnswer
import hs.kr.entrydsm.admin.domain.port.out.QuestionAnswerRepository
import hs.kr.entrydsm.notification.api.AnswerQuestionRequest
import hs.kr.entrydsm.notification.api.NotificationApi
import org.springframework.stereotype.Component

/**
 * 질문과 답변은 notification 이 소유하므로 답변 등록도 그 모듈의 공개 API 로 넘깁니다.
 *
 * 답변은 질문 행에 직접 붙으므로 별도 답변 식별자가 없고, 질문 식별자를 그대로 돌려준다.
 */
@Component
class NotificationApiQuestionAnswerAdapter(
    private val notificationApi: NotificationApi,
) : QuestionAnswerRepository {
    override fun save(questionAnswer: QuestionAnswer): QuestionAnswer {
        val answered = callNotification(notFound = ErrorCode.QUESTION_NOT_FOUND) {
            notificationApi.answerQuestion(
                AnswerQuestionRequest(
                    questionId = questionAnswer.questionId,
                    content = questionAnswer.content,
                    answeredBy = questionAnswer.answeredBy,
                ),
            )
        }
        return questionAnswer.copy(id = answered.questionId, answeredAt = answered.answeredAt)
    }
}
