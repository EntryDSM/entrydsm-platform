package hs.kr.entrydsm.admin.application

import hs.kr.entrydsm.admin.domain.command.AnswerQuestionCommand
import hs.kr.entrydsm.admin.domain.command.CreateNoticeCommand
import hs.kr.entrydsm.admin.domain.model.Notice
import hs.kr.entrydsm.admin.domain.model.QuestionAnswer
import hs.kr.entrydsm.admin.domain.port.`in`.AnswerQuestionUseCase
import hs.kr.entrydsm.admin.domain.port.`in`.CreateNoticeUseCase
import hs.kr.entrydsm.admin.domain.port.out.NoticeRepository
import hs.kr.entrydsm.admin.domain.port.out.QuestionAnswerRepository
import org.springframework.stereotype.Service

/**
 * 공지와 질문 답변은 notification 이 소유합니다.
 *
 * 두 저장소 모두 gRPC 로 나가고 admin DB 를 건드리지 않아 트랜잭션을 열지 않는다.
 */
@Service
class SupportService(
    private val noticeRepository: NoticeRepository,
    private val questionAnswerRepository: QuestionAnswerRepository,
) : CreateNoticeUseCase,
    AnswerQuestionUseCase {

    override fun create(command: CreateNoticeCommand): Notice =
        noticeRepository.save(
            Notice(
                title = command.title,
                content = command.content,
                division = command.division,
                isPinned = command.isPinned,
                attachmentIds = command.attachmentIds,
            ),
        )

    override fun answer(command: AnswerQuestionCommand): QuestionAnswer =
        questionAnswerRepository.save(
            QuestionAnswer(
                questionId = command.questionId,
                content = command.content,
                answeredBy = command.answeredBy,
            ),
        )
}
