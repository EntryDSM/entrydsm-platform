package hs.kr.entrydsm.identity.application

import hs.kr.entrydsm.identity.application.mock.MockIdentityPortAdapterTest
import hs.kr.entrydsm.identity.application.security.jwt.JwtTokenGeneratorTest
import org.junit.runner.RunWith
import org.junit.runners.Suite

@RunWith(Suite::class)
@Suite.SuiteClasses(
    MockIdentityPortAdapterTest::class,
    JwtTokenGeneratorTest::class,
)
class IdentityApplicationModuleTest
