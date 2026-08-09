package hs.kr.entrydsm.observability.application

import org.junit.runner.RunWith
import org.junit.runners.Suite

@RunWith(Suite::class)
@Suite.SuiteClasses(
    SessionCollectionServiceTest::class,
    MonitorHealthServiceTest::class,
    MonitorDashboardServiceTest::class,
    MetricsSeriesServiceTest::class,
    ClientLogCollectionServiceTest::class,
    ClientLogQueryServiceTest::class,
    ServerLogQueryServiceTest::class,
)
class ObservabilityApplicationModuleTest
