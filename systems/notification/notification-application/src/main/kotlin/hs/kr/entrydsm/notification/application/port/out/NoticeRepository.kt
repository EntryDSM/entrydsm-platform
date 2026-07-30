package hs.kr.entrydsm.notification.application.port.out

import hs.kr.entrydsm.notification.domain.model.Notice

interface NoticeRepository {
    fun findAll(): List<Notice>
    fun findById(id: Long): Notice?
}

