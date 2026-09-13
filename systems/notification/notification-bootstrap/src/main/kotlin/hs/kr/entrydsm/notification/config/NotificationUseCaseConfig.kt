package hs.kr.entrydsm.notification.config

import hs.kr.entrydsm.notification.application.port.`in`.NotificationPort
import hs.kr.entrydsm.notification.application.port.out.FaqRepository
import hs.kr.entrydsm.notification.application.port.out.NoticeRepository
import hs.kr.entrydsm.notification.application.port.out.RecruitmentGuidelineRepository
import hs.kr.entrydsm.notification.application.service.NotificationService
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
class NotificationUseCaseConfig {
    @Bean
    fun notificationService(
        noticeRepository: NoticeRepository,
        faqRepository: FaqRepository,
        recruitmentGuidelineRepository: RecruitmentGuidelineRepository,
    ): NotificationPort = NotificationService(noticeRepository, faqRepository, recruitmentGuidelineRepository)
}
