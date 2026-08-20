package hs.kr.entrydsm.observability.adapterin

import hs.kr.entrydsm.observability.adapterin.web.ClientIpResolverTest
import hs.kr.entrydsm.observability.adapterin.web.exception.GlobalExceptionHandlerTest
import org.junit.runner.RunWith
import org.junit.runners.Suite

@RunWith(Suite::class)
@Suite.SuiteClasses(
    ClientIpResolverTest::class,
    GlobalExceptionHandlerTest::class,
)
class ObservabilityAdapterInModuleTest
