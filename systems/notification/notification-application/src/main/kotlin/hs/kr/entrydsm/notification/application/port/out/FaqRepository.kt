package hs.kr.entrydsm.notification.application.port.out

import hs.kr.entrydsm.notification.application.port.`in`.command.AnswerQuestionCommand
import hs.kr.entrydsm.notification.application.port.`in`.command.ReadFaqPageCommand
import hs.kr.entrydsm.notification.application.port.out.data.PageData
import hs.kr.entrydsm.notification.domain.model.Faq

interface FaqRepository {
    fun findPage(command: ReadFaqPageCommand): PageData<Faq>
    fun findById(id: Long): Faq?

    /** 질문에 답변을 붙입니다. 질문이 없으면 null 을 돌려줍니다. */
    fun answer(command: AnswerQuestionCommand): Faq?
}

