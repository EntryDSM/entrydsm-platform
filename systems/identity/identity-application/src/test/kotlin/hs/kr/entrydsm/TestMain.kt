package hs.kr.entrydsm.identity.application

import hs.kr.entrydsm.identity.application.service.AccountServiceTest
import hs.kr.entrydsm.identity.application.security.jwt.JwtTokenGeneratorTest
import hs.kr.entrydsm.identity.application.service.IdentityServiceTest
import org.junit.runner.RunWith
import org.junit.runners.Suite

@RunWith(Suite::class)
@Suite.SuiteClasses(
    AuthIdentityApplicationModuleTest::class,
    AccountServiceTest::class,
    ApplicationResultTest::class,
    JwtTokenGeneratorTest::class,
    IdentityServiceTest::class,
)
class IdentityApplicationModuleTest
