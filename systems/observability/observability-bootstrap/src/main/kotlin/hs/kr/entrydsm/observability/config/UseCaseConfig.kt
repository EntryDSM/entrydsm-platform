package hs.kr.entrydsm.observability.config

import hs.kr.entrydsm.observability.application.SessionCollectionService
import hs.kr.entrydsm.observability.application.port.out.RateLimitPort
import hs.kr.entrydsm.observability.application.port.out.SessionStorePort
import java.time.Clock
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/** 유스케이스는 Spring을 모르는 순수 클래스로 두고, 빈 등록만 bootstrap에서 담당한다. */
@Configuration(proxyBeanMethods = false)
class UseCaseConfig {

    @Bean
    fun sessionCollectionService(
        sessionStorePort: SessionStorePort,
        rateLimitPort: RateLimitPort,
        clock: Clock,
    ) = SessionCollectionService(sessionStorePort, rateLimitPort, clock)
}
