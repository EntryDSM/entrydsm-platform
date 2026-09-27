package hs.kr.entrydsm.observability.adapterout

import hs.kr.entrydsm.observability.adapterout.redis.RedisMetricsStoreAdapterTest
import hs.kr.entrydsm.observability.adapterout.report.XlsxCsvReportGeneratorTest
import org.junit.runner.RunWith
import org.junit.runners.Suite

@RunWith(Suite::class)
@Suite.SuiteClasses(
    RedisMetricsStoreAdapterTest::class,
    XlsxCsvReportGeneratorTest::class,
)
class ObservabilityAdapterOutModuleTest
