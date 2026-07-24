package hs.kr.entrydsm.identity.application

import hs.kr.entrydsm.identity.application.security.jwt.JwtTokenGeneratorTest
import org.junit.runner.RunWith
import org.junit.runners.Suite

@RunWith(Suite::class)
@Suite.SuiteClasses(
    ApplicationResultTest::class,
    JwtTokenGeneratorTest::class,
)
class IdentityApplicationModuleTest
