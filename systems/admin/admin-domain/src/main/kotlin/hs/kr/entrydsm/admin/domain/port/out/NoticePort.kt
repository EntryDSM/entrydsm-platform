package hs.kr.entrydsm.admin.domain.port.out

import hs.kr.entrydsm.admin.domain.command.CreateNoticeCommand
import hs.kr.entrydsm.admin.domain.model.Notice

/**
 * 공지사항은 notification 시스템이 소유합니다. admin 은 등록만 위임합니다.
 */
interface NoticePort {
    fun create(command: CreateNoticeCommand): Notice
}
