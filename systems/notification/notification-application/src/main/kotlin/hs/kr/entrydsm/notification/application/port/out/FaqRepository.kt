package hs.kr.entrydsm.notification.application.port.out

import hs.kr.entrydsm.notification.domain.model.Faq

interface FaqRepository {
    fun findAll(): List<Faq>
    fun findById(id: Long): Faq?
}

