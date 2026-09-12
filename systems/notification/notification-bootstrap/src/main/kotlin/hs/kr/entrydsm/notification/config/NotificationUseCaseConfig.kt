package hs.kr.entrydsm.notification.config

import hs.kr.entrydsm.notification.application.port.out.FaqRepository
import hs.kr.entrydsm.notification.application.port.out.NoticeRepository
import hs.kr.entrydsm.notification.application.port.out.RecruitmentGuidelineRepository
import hs.kr.entrydsm.notification.application.service.NotificationService
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
class NotificationUseCaseConfig {
    /**
     * 조회(`NotificationPort`)와 공지 등록(`CreateNoticeUseCase`)을 한 인스턴스가 구현하므로
     * 구현 타입으로 노출해 두 포트 모두 주입되게 합니다.
     */
    @Bean
    fun notificationService(
        noticeRepository: NoticeRepository,
        faqRepository: FaqRepository,
        recruitmentGuidelineRepository: RecruitmentGuidelineRepository,
    ): NotificationService = NotificationService(noticeRepository, faqRepository, recruitmentGuidelineRepository)
}
