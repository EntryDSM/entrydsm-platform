package hs.kr.entrydsm.notification.application.port.out

import hs.kr.entrydsm.notification.application.port.`in`.command.CreateNoticeCommand
import hs.kr.entrydsm.notification.application.port.`in`.command.ReadNotificationPageCommand
import hs.kr.entrydsm.notification.application.port.`in`.command.UpdateNoticeCommand
import hs.kr.entrydsm.notification.application.port.out.data.PageData
import hs.kr.entrydsm.notification.domain.model.Notice

interface NoticeRepository {
    fun findPage(command: ReadNotificationPageCommand): PageData<Notice>
    fun findById(id: Long): Notice?
    fun create(command: CreateNoticeCommand): Notice

    /** 값이 있는 필드만 바꿉니다. 공지가 없으면 null 을 돌려줍니다. */
    fun update(command: UpdateNoticeCommand): Notice?

    /** 공지를 지웁니다. 공지가 없으면 false 를 돌려줍니다. */
    fun deleteById(id: Long): Boolean
}

