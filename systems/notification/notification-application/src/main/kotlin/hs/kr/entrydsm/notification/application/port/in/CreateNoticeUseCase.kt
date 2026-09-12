package hs.kr.entrydsm.notification.application.port.`in`

import hs.kr.entrydsm.notification.application.port.`in`.command.CreateNoticeCommand
import hs.kr.entrydsm.notification.application.port.`in`.result.NoticeDetailResult

/**
 * 공지사항 등록은 notification 이 소유합니다. admin 은 gRPC 로 이 유스케이스를 호출합니다.
 */
interface CreateNoticeUseCase {
    fun createNotice(command: CreateNoticeCommand): NoticeDetailResult
}
