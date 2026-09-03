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
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class SupportService(
    private val noticeRepository: NoticeRepository,
    private val questionAnswerRepository: QuestionAnswerRepository,
) : CreateNoticeUseCase,
    AnswerQuestionUseCase {

    @Transactional
    override fun create(command: CreateNoticeCommand): Notice =
        noticeRepository.save(
            Notice(
                title = command.title,
                content = command.content,
                isPinned = command.isPinned,
                attachmentIds = command.attachmentIds,
            ),
        )

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
