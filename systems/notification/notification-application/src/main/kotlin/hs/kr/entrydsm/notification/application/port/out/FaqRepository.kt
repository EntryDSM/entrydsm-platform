package hs.kr.entrydsm.notification.application.port.out

import hs.kr.entrydsm.notification.application.port.`in`.command.ReadFaqPageCommand
import hs.kr.entrydsm.notification.application.port.out.data.PageData
import hs.kr.entrydsm.notification.domain.model.Faq

interface FaqRepository {
    fun findPage(command: ReadFaqPageCommand): PageData<Faq>
    fun findById(id: Long): Faq?
}

