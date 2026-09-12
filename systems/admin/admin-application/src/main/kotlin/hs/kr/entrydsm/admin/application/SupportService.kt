package hs.kr.entrydsm.admin.application

import hs.kr.entrydsm.admin.domain.command.AnswerQuestionCommand
import hs.kr.entrydsm.admin.domain.command.CreateNoticeCommand
import hs.kr.entrydsm.admin.domain.model.Notice
import hs.kr.entrydsm.admin.domain.model.QuestionAnswer
import hs.kr.entrydsm.admin.domain.port.`in`.AnswerQuestionUseCase
import hs.kr.entrydsm.admin.domain.port.`in`.CreateNoticeUseCase
import hs.kr.entrydsm.admin.domain.port.out.NoticePort
import hs.kr.entrydsm.admin.domain.port.out.QuestionAnswerRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class SupportService(
    private val noticePort: NoticePort,
    private val questionAnswerRepository: QuestionAnswerRepository,
) : CreateNoticeUseCase,
    AnswerQuestionUseCase {

    /**
     * 공지사항은 notification 시스템에 저장됩니다. admin DB 트랜잭션과 무관하므로 열지 않습니다.
     */
    override fun create(command: CreateNoticeCommand): Notice = noticePort.create(command)

    @Transactional
    override fun answer(command: AnswerQuestionCommand): QuestionAnswer =
        questionAnswerRepository.save(
            QuestionAnswer(
                questionId = command.questionId,
                content = command.content,
                answeredBy = command.answeredBy,
            ),
        )
}
