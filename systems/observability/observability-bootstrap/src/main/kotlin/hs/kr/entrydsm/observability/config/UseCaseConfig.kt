package hs.kr.entrydsm.observability.config

import hs.kr.entrydsm.observability.application.MetricsSeriesService
import hs.kr.entrydsm.observability.application.MonitorDashboardService
import hs.kr.entrydsm.observability.application.MonitorHealthService
import hs.kr.entrydsm.observability.application.SessionCollectionService
import hs.kr.entrydsm.observability.application.port.out.ClientLogStorePort
import hs.kr.entrydsm.observability.application.port.out.HealthCheckPort
import hs.kr.entrydsm.observability.application.port.out.MetricsStorePort
import hs.kr.entrydsm.observability.application.port.out.RateLimitPort
import hs.kr.entrydsm.observability.application.port.out.RoundPort
import hs.kr.entrydsm.observability.application.port.out.SessionStorePort
import hs.kr.entrydsm.observability.application.port.out.StorageUsagePort
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
        metricsStorePort: MetricsStorePort,
        clock: Clock,
    ) = SessionCollectionService(sessionStorePort, rateLimitPort, metricsStorePort, clock)

    @Bean
    fun monitorHealthService(
        healthCheckPort: HealthCheckPort,
        clock: Clock,
    ) = MonitorHealthService(healthCheckPort, clock)

    @Bean
    fun monitorDashboardService(
        sessionStorePort: SessionStorePort,
        healthCheckPort: HealthCheckPort,
        clientLogStorePort: ClientLogStorePort,
        storageUsagePort: StorageUsagePort,
        roundPort: RoundPort,
        clock: Clock,
    ) = MonitorDashboardService(sessionStorePort, healthCheckPort, clientLogStorePort, storageUsagePort, roundPort, clock)

    @Bean
    fun metricsSeriesService(
        metricsStorePort: MetricsStorePort,
        clock: Clock,
    ) = MetricsSeriesService(metricsStorePort, clock)
}
