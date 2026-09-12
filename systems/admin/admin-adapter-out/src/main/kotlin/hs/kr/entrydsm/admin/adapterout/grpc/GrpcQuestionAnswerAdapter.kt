package hs.kr.entrydsm.admin.adapterout.grpc

import hs.kr.entrydsm.admin.domain.enum.ErrorCode
import hs.kr.entrydsm.admin.domain.model.QuestionAnswer
import hs.kr.entrydsm.admin.domain.port.out.QuestionAnswerRepository
import hs.kr.entrydsm.notification.grpc.AnswerQuestionRequest
import hs.kr.entrydsm.notification.grpc.NotificationServiceGrpc
import io.grpc.StatusRuntimeException
import java.time.Instant
import java.util.concurrent.TimeUnit
import org.springframework.stereotype.Component

/**
 * 질문과 답변은 notification 이 소유하므로 답변 등록도 gRPC 로 넘깁니다.
 *
 * 답변은 질문 행에 직접 붙으므로 별도 답변 식별자가 없고, 질문 식별자를 그대로 돌려준다.
 */
@Component
class GrpcQuestionAnswerAdapter(
    private val grpc: NotificationGrpcChannel,
) : QuestionAnswerRepository {
    private val stub = NotificationServiceGrpc.newBlockingStub(grpc.channel)

    override fun save(questionAnswer: QuestionAnswer): QuestionAnswer {
        val response = try {
            stub.withDeadlineAfter(grpc.deadlineMs, TimeUnit.MILLISECONDS).answerQuestion(
                AnswerQuestionRequest.newBuilder()
                    .setQuestionId(questionAnswer.questionId)
                    .setContent(questionAnswer.content)
                    .setAnsweredBy(questionAnswer.answeredBy)
                    .build(),
            )
        } catch (exception: StatusRuntimeException) {
            throw exception.toAdminException(notFound = ErrorCode.QUESTION_NOT_FOUND)
        }
        return questionAnswer.copy(
            id = response.questionId,
            answeredAt = Instant.ofEpochMilli(response.answeredAtEpochMillis),
        )
    }
}
