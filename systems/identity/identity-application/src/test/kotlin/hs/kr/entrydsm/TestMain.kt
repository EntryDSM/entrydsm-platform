package hs.kr.entrydsm.identity.application

import hs.kr.entrydsm.identity.application.mock.MockAuthPortAdapterTest
import hs.kr.entrydsm.identity.application.security.jwt.JwtTokenVerifierTest
import hs.kr.entrydsm.identity.application.security.jwt.JwtTokenGeneratorTest
import hs.kr.entrydsm.identity.application.service.AuthServiceTest
import hs.kr.entrydsm.identity.application.service.IdentityResultMapperTest
import hs.kr.entrydsm.identity.application.service.IdentityServiceSupportTest
import org.junit.runner.RunWith
import org.junit.runners.Suite

@RunWith(Suite::class)
@Suite.SuiteClasses(
    ApplicationResultTest::class,
    MockAuthPortAdapterTest::class,
    JwtTokenGeneratorTest::class,
    JwtTokenVerifierTest::class,
    AuthServiceTest::class,
    IdentityResultMapperTest::class,
    IdentityServiceSupportTest::class,
)
class IdentityApplicationModuleTest
