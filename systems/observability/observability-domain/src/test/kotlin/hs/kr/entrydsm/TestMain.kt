package hs.kr.entrydsm.observability.domain

import org.junit.runner.RunWith
import org.junit.runners.Suite

@RunWith(Suite::class)
@Suite.SuiteClasses(
    CursorTest::class,
    HealthStatusClassifierTest::class,
    DeviceTypeParserTest::class,
)
class ObservabilityDomainModuleTest
