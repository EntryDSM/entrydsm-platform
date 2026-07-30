package hs.kr.entrydsm.notification.application.port.`in`

import hs.kr.entrydsm.notification.application.port.`in`.command.ReadNotificationPageCommand
import hs.kr.entrydsm.notification.application.port.`in`.result.FaqDetailResult
import hs.kr.entrydsm.notification.application.port.`in`.result.FaqSummaryResult
import hs.kr.entrydsm.notification.application.port.`in`.result.NoticeDetailResult
import hs.kr.entrydsm.notification.application.port.`in`.result.NoticeSummaryResult
import hs.kr.entrydsm.notification.application.port.`in`.result.PageResult
import hs.kr.entrydsm.notification.application.port.`in`.result.RecruitmentGuidelineResult

interface NotificationPort {
    fun getNotices(command: ReadNotificationPageCommand): PageResult<NoticeSummaryResult>
    fun getNotice(id: Long): NoticeDetailResult
    fun getFaqs(command: ReadNotificationPageCommand): PageResult<FaqSummaryResult>
    fun getFaq(id: Long): FaqDetailResult
    fun getRecruitmentGuideline(): RecruitmentGuidelineResult
}

