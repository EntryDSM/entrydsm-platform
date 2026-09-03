package hs.kr.entrydsm.notification.application.port.out

import hs.kr.entrydsm.notification.application.port.`in`.command.ReadNotificationPageCommand
import hs.kr.entrydsm.notification.application.port.out.data.PageData
import hs.kr.entrydsm.notification.domain.model.Notice

interface NoticeRepository {
    fun findPage(command: ReadNotificationPageCommand): PageData<Notice>
    fun findById(id: Long): Notice?
}

