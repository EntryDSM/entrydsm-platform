package hs.kr.entrydsm.identity.application

import hs.kr.entrydsm.identity.application.service.AccountServiceTest
import org.junit.runner.RunWith
import org.junit.runners.Suite

@RunWith(Suite::class)
@Suite.SuiteClasses(
    AuthIdentityApplicationModuleTest::class,
    AccountServiceTest::class,
)
class IdentityApplicationModuleTest
