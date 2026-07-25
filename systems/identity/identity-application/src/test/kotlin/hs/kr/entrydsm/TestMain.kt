package hs.kr.entrydsm.identity.application

import hs.kr.entrydsm.identity.application.security.jwt.JwtTokenGeneratorTest
import hs.kr.entrydsm.identity.application.service.AuthServiceTest
import org.junit.runner.RunWith
import org.junit.runners.Suite

@RunWith(Suite::class)
@Suite.SuiteClasses(
    ApplicationResultTest::class,
    JwtTokenGeneratorTest::class,
    AuthServiceTest::class,
)
class IdentityApplicationModuleTest
