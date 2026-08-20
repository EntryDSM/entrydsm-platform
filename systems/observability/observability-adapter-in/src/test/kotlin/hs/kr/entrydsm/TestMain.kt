package hs.kr.entrydsm.observability.adapterin

import hs.kr.entrydsm.observability.adapterin.web.ClientIpResolverTest
import hs.kr.entrydsm.observability.adapterin.web.exception.GlobalExceptionHandlerTest
import hs.kr.entrydsm.observability.adapterin.web.security.JwtAuthInterceptorTest
import org.junit.runner.RunWith
import org.junit.runners.Suite

@RunWith(Suite::class)
@Suite.SuiteClasses(
    ClientIpResolverTest::class,
    GlobalExceptionHandlerTest::class,
    JwtAuthInterceptorTest::class,
)
class ObservabilityAdapterInModuleTest
